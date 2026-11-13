package ctbrec.recorder.download.hls;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.*;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.io.HttpClient;
import ctbrec.io.IoUtils;
import ctbrec.recorder.download.hls.SegmentPlaylist.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.regex.Pattern;

public class HlsDownload extends AbstractHlsDownload {

    private static final Logger LOG = LoggerFactory.getLogger(HlsDownload.class);

    protected Path downloadDir;

    private final Queue<Future<SegmentDownload>> segmentDownloads = new LinkedList<>();

    private final List<TrackData> segments = new LinkedList<>();

    private float targetDuration;

    public HlsDownload(HttpClient client) {
        super(client);
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        super.init(config, model, startTime, executorService);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Config.RECORDING_DATE_FORMAT);
        String formattedStartTime = formatter.format(ZonedDateTime.ofInstant(this.startTime, ZoneId.systemDefault()));
        Path modelDir = FileSystems.getDefault().getPath(config.getSettings().recordingsDir, model.getSanitizedNamed());
        downloadDir = FileSystems.getDefault().getPath(modelDir.toString(), formattedStartTime);
        createTargetDirectory();
    }

    @Override
    public AbstractHlsDownload call() throws Exception {
        super.call();
        updatePlaylist(true);
        return this;
    }

    @Override
    protected SegmentPlaylist getNextSegments(String segmentPlaylistUrl) throws IOException, ParseException, PlaylistException {
        SegmentPlaylist segmentPlaylist = super.getNextSegments(segmentPlaylistUrl);
        targetDuration = segmentPlaylist.targetDuration;
        return segmentPlaylist;
    }

    private void updatePlaylist(boolean ongoing) {
        downloadExecutor.submit(() -> {
            addNewSegmentsToPlaylist();
            if (segments.isEmpty() || targetDuration <= 0) {
                return;
            }
            try {
                MediaPlaylist playlist = new MediaPlaylist.Builder()
                        .withPlaylistType(ongoing ? PlaylistType.EVENT : PlaylistType.VOD)
                        .withIsOngoing(ongoing)
                        .withMediaSequenceNumber(0)
                        .withTargetDuration(Math.round(targetDuration))
                        .withTracks(segments)
                        .build();

                // create a master playlist containing the media playlist
                Playlist master = new Playlist.Builder()
                        .withCompatibilityVersion(4)
                        .withExtended(true)
                        .withMediaPlaylist(playlist)
                        .build();

                // write the playlist to a file
                File output = new File(getTarget(), "playlist.m3u8");
                try (FileOutputStream fos = new FileOutputStream(output)) {
                    PlaylistWriter writer = new PlaylistWriter.Builder()
                            .withFormat(Format.EXT_M3U)
                            .withEncoding(Encoding.UTF_8)
                            .withOutputStream(fos)
                            .build();
                    writer.write(master);
                }
            } catch (PlaylistException e) {
                LOG.error("Updating segment playlist failed", e);
                if (e.getErrors() != null) {
                    for (PlaylistError error : e.getErrors()) {
                        LOG.error("Playlist Error: {}", error);
                    }
                }
            } catch (IOException | ParseException e) {
                LOG.error("Updating segment playlist failed", e);
            }
            LOG.trace("Segment queue size for {}: {}", model, segmentDownloads.size());
        });
    }

    private void addNewSegmentsToPlaylist() {
        Future<SegmentDownload> future;
        while ((future = segmentDownloads.peek()) != null && !Thread.currentThread().isInterrupted()) {
            try {
                if (running && future.isDone()) {
                    segmentDownloads.poll();// future is done remove from queue
                    SegmentDownload segmentDownload = future.get();
                    segments.add(toTrack(segmentDownload.getSegment()));
                } else {
                    // first download in queue not finished, let's continue with other stuff
                    break;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.info("Segment download failed for model {} - skipping adding segment to playlist", model);
            }
        }
    }

    private TrackData toTrack(Segment segment) {
        String filename = segment.targetFile.getName();
        return new TrackData.Builder()
                .withUri(filename)
                .withTrackInfo(new TrackInfo(segment.duration, filename))
                .build();
    }

    protected void createTargetDirectory() throws IOException {
        if (!downloadDir.toFile().exists()) {
            Files.createDirectories(downloadDir);
        }
    }

    @Override
    public void finalizeDownload() {
        updatePlaylist(false);
        LOG.debug("Download for {} terminated", model);
    }

    @Override
    public void postProcess(Recording recording) {
        // nothing to do
    }

    @Override
    public void stop() {
        LOG.debug("Recording stopped");
        internalStop();
    }

    @Override
    protected void internalStop() {
        running = false;
    }

    @Override
    public File getTarget() {
        return downloadDir.toFile();
    }

    @Override
    public String getPath(Model model) {
        String absolutePath = getTarget().getAbsolutePath();
        String recordingsDir = Config.getInstance().getSettings().recordingsDir;
        String relativePath = absolutePath.replaceFirst(Pattern.quote(recordingsDir), "");
        return relativePath;
    }

    @Override
    public boolean isSingleFile() {
        return false;
    }

    @Override
    public long getSizeInByte() {
        if (super.getSizeInByte() == 0) {
            return IoUtils.getDirectorySize(getTarget());
        } else {
            return super.getSizeInByte();
        }
    }

    @Override
    protected OutputStream getSegmentOutputStream(Segment segment) throws FileNotFoundException, MalformedURLException {
        URL segmentUrl = new URL(segment.url);
        File tmp = new File(segmentUrl.getFile());
        String prefixedFileName = segment.prefix + '_' + tmp.getName();
        int questionMarkPosition = prefixedFileName.indexOf('?');
        if (questionMarkPosition > 0) {
            prefixedFileName = prefixedFileName.substring(0, questionMarkPosition);
        }
        if (!prefixedFileName.endsWith(".ts")) {
            prefixedFileName += ".ts";
        }
        segment.targetFile = FileSystems.getDefault().getPath(downloadDir.toAbsolutePath().toString(), prefixedFileName).toFile();
        return new FileOutputStream(segment.targetFile);
    }

    @Override
    protected SegmentDownload getSegmentDownload(SegmentPlaylist playlist, Segment segment, OutputStream targetStream) throws MalformedURLException {
        return new MultiFileSegmentDownload(getModel(), playlist, segment, client, targetStream);
    }

    @Override
    protected void execute(SegmentDownload segmentDownload) {
        segmentDownloads.add(segmentDownloadService.submit(segmentDownload));
    }
}
