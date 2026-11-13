package ctbrec.recorder.download.hls;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MediaPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.TrackData;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.OS;
import ctbrec.Recording;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.recorder.FFmpeg;
import ctbrec.recorder.InvalidPlaylistException;
import ctbrec.recorder.download.AbstractDownload;
import ctbrec.recorder.download.ProcessExitedUncleanException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;

public class FfmpegHlsDownload extends AbstractDownload {

    private static final Logger LOG = LoggerFactory.getLogger(FfmpegHlsDownload.class);
    private static final int MAX_SECONDS_WITHOUT_TRANSFER = 20;

    private final HttpClient httpClient;
    private Instant timeOfLastTransfer = Instant.MAX;

    protected File targetFile;
    protected FFmpeg ffmpeg;
    protected Process ffmpegProcess;
    protected OutputStream ffmpegStdIn;
    protected Lock ffmpegStreamLock = new ReentrantLock();
    protected String mediaUrl = null;

    private volatile boolean running;
    private volatile boolean started;

    public FfmpegHlsDownload(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        super.init(config, model, startTime, executorService);

        timeOfLastTransfer = Instant.now();
        String fileSuffix = config.getSettings().ffmpegFileSuffix;

        targetFile = config.getFileForRecording(model, fileSuffix, startTime);
        createTargetDirectory();
        startFfmpegProcess(targetFile);
        if (ffmpegProcess == null) {
            throw new ProcessExitedUncleanException("Couldn't spawn FFmpeg");
        }
    }

    @Override
    public void stop() {
        if (running) {
            internalStop();
        }
    }

    private synchronized void internalStop() {
        running = false;
        if (ffmpegStdIn != null) {
            try {
                ffmpegStdIn.close();
            } catch (IOException e) {
                LOG.error("Couldn't terminate FFmpeg by closing stdin", e);
            }
        }
        if (ffmpegProcess != null) {
            try {
                boolean waitFor = ffmpegProcess.waitFor(45, TimeUnit.SECONDS);
                if (!waitFor && ffmpegProcess.isAlive()) {
                    ffmpegProcess.destroy();
                    if (ffmpegProcess.isAlive()) {
                        LOG.info("FFmpeg didn't terminate. Destroying the process with force!");
                        ffmpegProcess.destroyForcibly();
                        ffmpegProcess = null;
                    }
                }
            } catch (InterruptedException e) {
                LOG.error("Interrupted while waiting for FFmpeg to terminate");
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startFfmpegProcess(File target) {
        try {
            String[] cmdline = prepareCommandLine(target);
            ffmpeg = new FFmpeg.Builder()
                    .logOutput(config.getSettings().logFFmpegOutput)
                    .onStarted(p -> {
                        ffmpegProcess = p;
                        ffmpegStdIn = ffmpegProcess.getOutputStream();
                    })
                    .build();
            ffmpeg.exec(cmdline, new String[0], target.getParentFile());
        } catch (IOException | ProcessExitedUncleanException e) {
            LOG.error("Error in FFmpeg thread", e);
        }
    }

    private String[] prepareCommandLine(File target) {
        String[] args = config.getSettings().ffmpegMergedDownloadArgs.split(" ");
        String[] argsPlusFile = new String[args.length + 3];
        int i = 0;
        argsPlusFile[i++] = "-i";
        argsPlusFile[i++] = "-";
        System.arraycopy(args, 0, argsPlusFile, i, args.length);
        argsPlusFile[argsPlusFile.length - 1] = target.getAbsolutePath();
        return OS.getFFmpegCommand(argsPlusFile);
    }

    @Override
    public void finalizeDownload() {
        internalStop();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void postProcess(Recording recording) {
        // nothing to do
    }

    @Override
    public File getTarget() {
        return targetFile;
    }

    @Override
    public String getPath(Model model) {
        String absolutePath = targetFile.getAbsolutePath();
        String recordingsDir = Config.getInstance().getSettings().recordingsDir;
        String relativePath = absolutePath.replaceFirst(Pattern.quote(recordingsDir), "");
        return relativePath;
    }

    @Override
    public boolean isSingleFile() {
        return true;
    }

    @Override
    public long getSizeInByte() {
        return getTarget().length();
    }

    @Override
    public RecordingProcess call() throws Exception {
        try {
            if (!ffmpegProcess.isAlive()) {
                running = false;
                int exitValue = ffmpegProcess.exitValue();
                ffmpeg.shutdown(exitValue);
            }
        } catch (ProcessExitedUncleanException e) {
            LOG.error("FFmpeg exited unclean", e);
            internalStop();
        }
        try {
            if (!started) {
                started = true;
                startDownload();
            }
        } catch (Exception e) {
            LOG.error("Error while downloading MP4", e);
            stop();
        }
        if (!model.isOnline()) {
            LOG.debug("Model {} not online. Stop recording.", model);
            stop();
        }
        if (splittingStrategy.splitNecessary(this)) {
            LOG.debug("Split necessary for model {}. Stop recording.", model);
            internalStop();
        } else {
            rescheduleTime = Instant.now().plusSeconds(5);
        }
        if (Duration.between(timeOfLastTransfer, Instant.now()).getSeconds() > MAX_SECONDS_WITHOUT_TRANSFER) {
            LOG.debug("No video data received for {} seconds. Stopping recording for model {}", MAX_SECONDS_WITHOUT_TRANSFER, model);
            stop();
        }
        return this;
    }

    private String getMediaUrl(Model model) throws IOException, ExecutionException, ParseException, PlaylistException, InvalidPlaylistException, JAXBException {
        List<StreamSource> streamSources = model.getStreamSources();
        Collections.sort(streamSources);
        for (StreamSource streamSource : streamSources) {
            LOG.debug("{}:{} src {}", model.getSite().getName(), model.getName(), streamSource);
        }
        StreamSource selectedStreamSource = selectStreamSource(streamSources);
        String playlistUrl = selectedStreamSource.getMediaPlaylistUrl();
        selectedResolution = selectedStreamSource.getHeight();

        Request req = new Request.Builder()
                .url(playlistUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "en")
                .header(ORIGIN, model.getSite().getBaseUrl())
                .header(REFERER, model.getSite().getBaseUrl())
                .build();
        try (Response response = model.getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                InputStream inputStream = Objects.requireNonNull(response.body()).byteStream();
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                MediaPlaylist media = playlist.getMediaPlaylist();
                if (media.hasTracks()) {
                    TrackData firstTrack = media.getTracks().get(0);
                    if (firstTrack.isEncrypted()) {
                        LOG.warn("Video track is encrypted. Playlist URL: {}", playlistUrl);
                    }
                    String uri = firstTrack.getUri();
                    if (!uri.startsWith("http")) {
                        URI context = URI.create(playlistUrl);
                        uri = context.resolve(uri).toURL().toExternalForm();
                    }
                    LOG.debug("Media url {}", uri);
                    return uri;
                } else {
                    throw new InvalidPlaylistException("Playlist has no media");
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void startDownload() {
        downloadExecutor.submit(() -> {
            running = true;
            ffmpegStreamLock.lock();
            try {
                if (mediaUrl == null) {
                    mediaUrl = getMediaUrl(model);
                }
                Request request = new Request.Builder()
                        .url(mediaUrl)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(ACCEPT, "*/*")
                        .header(ACCEPT_LANGUAGE, "en")
                        .header(ORIGIN, model.getSite().getBaseUrl())
                        .header(REFERER, model.getSite().getBaseUrl())
                        .build();
                try (Response resp = httpClient.execute(request)) {
                    if (resp.isSuccessful()) {
                        LOG.debug("Recording video stream to {}", targetFile);
                        InputStream in = Objects.requireNonNull(resp.body()).byteStream();
                        byte[] b = new byte[1024 * 4];
                        int len;
                        while (running && !Thread.currentThread().isInterrupted() && (len = in.read(b)) >= 0) {
                            ffmpegStdIn.write(b, 0, len);
                            timeOfLastTransfer = Instant.now();
                            BandwidthMeter.add(len);
                        }
                    } else {
                        throw new HttpException(resp.code(), resp.message());
                    }
                }
            } catch (SocketTimeoutException e) {
                LOG.debug("Socket timeout while downloading MP4 for {}. Stop recording.", model.getName());
                model.delay();
                stop();
            } catch (IOException e) {
                LOG.debug("IO error while downloading MP4 for {}. Stop recording.", model.getName());
                model.delay();
                stop();
            } catch (Exception e) {
                LOG.error("Error while downloading MP4", e);
                stop();
            } finally {
                ffmpegStreamLock.unlock();
            }
            LOG.debug("Record finished for model {}", model);
            running = false;
        });
    }

    protected void createTargetDirectory() throws IOException {
        Files.createDirectories(targetFile.getParentFile().toPath());
    }
}
