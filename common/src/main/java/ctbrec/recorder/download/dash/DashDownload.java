package ctbrec.recorder.download.dash;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.io.IoUtils;
import ctbrec.recorder.download.AbstractDownload;
import ctbrec.recorder.download.dash.SegmentTimelineType.S;
import ctbrec.recorder.download.hls.NoStreamFoundException;
import ctbrec.recorder.download.hls.PostProcessingException;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static ctbrec.Recording.State.POST_PROCESSING;
import static ctbrec.io.HttpConstants.*;

public class DashDownload extends AbstractDownload {
    private static final String CONTENT_LENGTH = "Content-Length";

    private static final Logger LOG = LoggerFactory.getLogger(DashDownload.class);

    private int audioCounter = 0;
    private int videoCounter = 0;
    private DecimalFormat df = new DecimalFormat("00000");
    private boolean videoInitLoaded = false;
    private boolean audioInitLoaded = false;
    private BigInteger lastAudioTimestamp = BigInteger.ZERO;
    private BigInteger lastVideoTimestamp = BigInteger.ZERO;
    private transient AtomicBoolean downloadFinished = new AtomicBoolean(false);
    private transient HttpClient httpClient;
    private transient Path downloadDir;
    private String manifestUrl;
    private boolean running = false;

    private File targetFile;

    public DashDownload(HttpClient httpClient, String manifestUrl) {
        this.httpClient = httpClient;
        this.manifestUrl = manifestUrl;
    }

    private String getManifest(String url) throws IOException, ExecutionException, InterruptedException {
        // @formatter:off
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, model.getSite().getBaseUrl())
                .header(REFERER, model.getSite().getBaseUrl())
                .header(CONNECTION, KEEP_ALIVE)
                .build(); // @formatter:on
        LOG.trace("Loading manifest {}", url);
        for (int tries = 1; tries <= 10; tries++) {
            try (Response response = httpClient.execute(request)) {
                if (response.isSuccessful()) {
                    return response.body().string();
                } else {
                    HttpException httpException = new HttpException(response.code(), "Couldn't load manifest: " + response.message());
                    if (model.isOnline(true)) {
                        if (tries == 10) {
                            throw httpException;
                        } else {
                            LOG.debug("Couldn't load manifest", httpException);
                            waitSomeTime(100l * tries);
                        }
                    } else {
                        internalStop();
                    }
                }
            }
        }
        throw new IOException("Couldn't load manifest");
    }

    private int downloadSegments(MPDtype mpd, AdaptationSetType adaptationSet, boolean isVideo) throws IOException {
        if (adaptationSet == null) {
            return 0;
        }

        SegmentTemplateType segmentTemplate = adaptationSet.getSegmentTemplate();
        RepresentationType representation = chooseBestRepresentation(adaptationSet.getRepresentation());

        int downloaded = downloadInitChunksForVideoAndAudio(isVideo, mpd, segmentTemplate, representation);

        String media = segmentTemplate.getMedia();
        media = media.replace("$RepresentationID$", representation.getId()); // NOSONAR

        List<S> segments = segmentTemplate.getSegmentTimeline().getS();
        if (!segments.isEmpty()) {
            BigInteger timestamp = segments.get(0).getT();
            BigInteger duration = BigInteger.ZERO;
            for (S s : segments) {
                timestamp = timestamp.add(duration);
                if (isVideo && timestamp.compareTo(lastVideoTimestamp) > 0 || !isVideo && timestamp.compareTo(lastAudioTimestamp) > 0) {
                    if (isVideo) {
                        lastVideoTimestamp = timestamp;
                    } else {
                        lastAudioTimestamp = timestamp;
                    }
                    String segmentUrl = media.replace("$Time$", timestamp.toString());
                    duration = s.getD();
                    URL absUrl = new URL(new URL(mpd.getLocation().get(0)), segmentUrl);
                    download(downloadDir.toFile().getCanonicalPath(), absUrl, isVideo);
                    downloaded++;
                }
            }
        }

        return downloaded;
    }

    private RepresentationType chooseBestRepresentation(List<RepresentationType> representations) {
        RepresentationType best = null;
        long bestBandwidth = 0;
        for (RepresentationType rep : representations) {
            if (rep.getBandwidth() > bestBandwidth) {
                bestBandwidth = rep.getBandwidth();
                best = rep;
            }
        }
        return best;
    }

    private int downloadInitChunksForVideoAndAudio(boolean isVideo, MPDtype mpd, SegmentTemplateType segmentTemplate, RepresentationType representation)
            throws IOException {
        int loadedFileCount = 0;
        if (isVideo && !videoInitLoaded || !isVideo && !audioInitLoaded) {
            String initialization = segmentTemplate.getInitializationAttribute();
            initialization = initialization.replace("$RepresentationID$", representation.getId());
            URL initUrl = new URL(new URL(mpd.getLocation().get(0)), initialization);
            File file = download(downloadDir.toFile().getCanonicalPath(), initUrl, isVideo);
            if (file != null) {
                setInitState(isVideo, file);
                loadedFileCount++;
            }
        }
        return loadedFileCount;
    }

    private void setInitState(boolean isVideo, File file) {
        if (isVideo) {
            if (file.exists() && file.length() > 0) {
                LOG.debug("Video init chunk loaded");
                videoInitLoaded = true;
            }
        } else {
            if (file.exists() && file.length() > 0) {
                LOG.debug("Audio init chunk loaded");
                audioInitLoaded = true;
            }
        }
    }

    private File download(String dir, URL url, boolean isVideo) throws IOException {
        // @formatter:off
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, model.getSite().getBaseUrl())
                .header(REFERER, model.getSite().getBaseUrl())
                .header(CONNECTION, KEEP_ALIVE)
                .build(); // @formatter:on
        int tries = 1;
        for (tries = 1; tries <= 10; tries++) {
            try (Response response = httpClient.execute(request)) {
                if (!response.isSuccessful()) {
                    LOG.trace("Loading segment failed, try {}, {} size:{} {}", tries, response.code(), response.headers().values(CONTENT_LENGTH), url);
                    waitSomeTime(tries * 80l);
                } else {
                    InputStream in = response.body().byteStream();
                    String absFile = url.getFile();
                    String prefix = isVideo ? "video" : "audio";
                    int c = isVideo ? videoCounter++ : audioCounter++;
                    File segmentFile = new File(dir, prefix + '_' + df.format(c) + '_' + new File(absFile).getName());
                    if (!segmentFile.exists() || segmentFile.length() == 0) {
                        try (FileOutputStream out = new FileOutputStream(segmentFile)) {
                            byte[] b = new byte[1024];
                            int len = -1;
                            while ((len = in.read(b)) >= 0) {
                                out.write(b, 0, len);
                                BandwidthMeter.add(len);
                            }
                            out.flush();
                        }
                    }
                    return segmentFile;
                }
            }
        }
        LOG.warn("Loading segment finally failed after {} tries {}", --tries, url);
        return null;
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) {
        this.config = config;
        this.model = model;
        this.startTime = startTime;
        File finalFile = Config.getInstance().getFileForRecording(model, "mp4", startTime);
        targetFile = new File(finalFile.getParentFile(), finalFile.getName() + ".part");
        downloadDir = targetFile.toPath();
    }

    @Override
    public DashDownload call() throws IOException {
        try {
            Thread.currentThread().setName("Download " + model.getName());
            running = true;
            JAXBContext jc = JAXBContext.newInstance(MPDtype.class.getPackage().getName());
            Unmarshaller u = jc.createUnmarshaller();
            while (running && !Thread.currentThread().isInterrupted()) {
                downloadManifestAndItsSegments(u);

                // split recordings
                boolean split = splitRecording();
                if (split) {
                    break;
                }
            }
        } catch (HttpException e) {
            if (e.getResponseCode() == 404) {
                LOG.debug("Manifest not found (404). Model {} probably went offline", model);
                waitSomeTime(10_000);
            } else if (e.getResponseCode() == 403) {
                LOG.debug("Manifest access forbidden (403). Model {} probably went private or offline", model);
                waitSomeTime(10_000);
            } else {
                throw e;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Error while downloading dash stream", e);
        } catch (Exception e) {
            LOG.error("Error while downloading dash stream", e);
        } finally {
            running = false;
            downloadFinished.set(true);
            synchronized (downloadFinished) {
                downloadFinished.notifyAll();
            }
        }
        return this;
    }

    private boolean splitRecording() {
        if (splittingStrategy.splitNecessary(this)) {
            internalStop();
            return true;
        } else {
            return false;
        }
    }

    private void downloadManifestAndItsSegments(Unmarshaller u) throws IOException, JAXBException, ExecutionException, InterruptedException {
        String manifest = getManifest(manifestUrl);
        LOG.trace("Manifest: {}", manifest);
        @SuppressWarnings("unchecked")
        JAXBElement<MPDtype> root = (JAXBElement<MPDtype>) u.unmarshal(new ByteArrayInputStream(manifest.getBytes()));
        MPDtype mpd = root.getValue();
        List<PeriodType> periods = mpd.getPeriod();
        for (PeriodType period : periods) {
            List<AdaptationSetType> videoStreams = new ArrayList<>();
            List<AdaptationSetType> audioStreams = new ArrayList<>();
            splitAdaptionSets(period.getAdaptationSet(), videoStreams, audioStreams);

            downloadDir.toFile().mkdirs();

            AdaptationSetType video = chooseBestVideo(videoStreams);
            if (video == null) {
                throw new ExecutionException(new NoStreamFoundException("No stream left in playlist"));
            } else {
                int downloaded = downloadSegments(mpd, video, true);

                AdaptationSetType audio = audioStreams.isEmpty() ? null : audioStreams.get(0);
                downloaded += downloadSegments(mpd, audio, false);

                if (downloaded == 0) {
                    LOG.trace("No new segments - Sleeping a bit");
                    waitSomeTime(1000);
                }
            }
        }
    }

    private void splitAdaptionSets(List<AdaptationSetType> adaptationSets, List<AdaptationSetType> videoStreams, List<AdaptationSetType> audioStreams) {
        for (AdaptationSetType adaptationSet : adaptationSets) {
            String mimeType = adaptationSet.getMimeType();
            if (mimeType.equalsIgnoreCase("video/mp4")) {
                videoStreams.add(adaptationSet);
            } else if (mimeType.equalsIgnoreCase("audio/mp4")) {
                audioStreams.add(adaptationSet);
            }
        }
    }

    private AdaptationSetType chooseBestVideo(List<AdaptationSetType> videoStreams) {
        AdaptationSetType best = null;
        int minHeight = config.getSettings().minimumResolution;
        int maxHeight = config.getSettings().maximumResolution;
        long bestHeight = 0;
        for (AdaptationSetType stream : videoStreams) {
            if (stream.getHeight() > bestHeight && (minHeight == 0 || stream.getHeight() >= minHeight) && (maxHeight == 0 || stream.getHeight() <= maxHeight)) {
                bestHeight = stream.getHeight();
                best = stream;
            }
        }
        return best;
    }

    @Override
    public void stop() {
        if (running) {
            internalStop();
            try {
                synchronized (downloadFinished) {
                    while (!downloadFinished.get()) {
                        downloadFinished.wait(TimeUnit.SECONDS.toMillis(1));
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Couldn't wait for download to finish", e);
            }
        }
    }

    void internalStop() {
        running = false;
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public void postProcess(Recording recording) {
        try {
            Thread.currentThread().setName("PP " + model.getName());
            recording.setStatus(POST_PROCESSING);
            // FIXME this was recording.getPath() before and is currently not working. This has to be fixed once DASH is used for a download again
            String path = recording.getAbsoluteFile().getAbsolutePath();
            File dir = new File(Config.getInstance().getSettings().recordingsDir, path);
            File file = new File(dir.getParentFile(), dir.getName().substring(0, dir.getName().length() - 5));
            new FfmpegMuxer(dir, file);
            targetFile = file;
            recording.setPath(path.substring(0, path.length() - 5));
        } catch (Exception e) {
            throw new PostProcessingException(e);
        }
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

    /**
     * Causes the current thread to sleep for a short amount of time. This is used to slow down retries, if something is wrong with the playlist. E.g. HTTP 403
     * or 404
     */
    private void waitSomeTime(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isSingleFile() {
        return false;
    }

    @Override
    public long getSizeInByte() {
        return IoUtils.getDirectorySize(downloadDir.toFile());
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public void finalizeDownload() {
        // nothing to do here
    }

}
