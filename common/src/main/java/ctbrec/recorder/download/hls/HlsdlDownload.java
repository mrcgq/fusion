package ctbrec.recorder.download.hls;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.OS;
import ctbrec.Recording;
import ctbrec.recorder.download.AbstractDownload;
import ctbrec.recorder.download.ProcessExitedUncleanException;
import ctbrec.recorder.download.StreamSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import static ctbrec.recorder.download.StreamSource.UNKNOWN;
import static java.util.concurrent.TimeUnit.SECONDS;

public class HlsdlDownload extends AbstractDownload {

    private static final Logger LOG = LoggerFactory.getLogger(HlsdlDownload.class);

    protected File targetFile;

    private transient Hlsdl hlsdl;
    protected transient Process hlsdlProcess;
    protected transient boolean running = true;
    protected transient Instant lastSizeChange = Instant.now();
    protected transient long lastSize = 0;

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        super.init(config, model, startTime, executorService);
        String fileSuffix = config.getSettings().ffmpegFileSuffix;
        targetFile = config.getFileForRecording(model, fileSuffix, startTime);

        createTargetDirectory();
        startHlsdlProcess();
        if (hlsdlProcess == null) {
            throw new ProcessExitedUncleanException("Couldn't spawn Hlsdl");
        }
    }

    protected void createTargetDirectory() throws IOException {
        Files.createDirectories(targetFile.getParentFile().toPath());
    }

    @Override
    public HlsdlDownload call() throws Exception {
        try {
            if (running && !hlsdlProcess.isAlive()) {
                running = false;
                int exitValue = hlsdlProcess.exitValue();
                hlsdl.shutdown(exitValue);
            }

            rescheduleTime = Instant.now().plusSeconds(1);

            if (splittingStrategy.splitNecessary(this)) {
                stop();
            }
            long size = getSizeInByte();
            if (size != lastSize) {
                lastSize = size;
                lastSizeChange = Instant.now();
            } else {
                int seconds = 90;
                if (Duration.between(lastSizeChange, Instant.now()).toMillis() > SECONDS.toMillis(seconds)) {
                    LOG.info("Recording size didn't change for {} secs. Stopping recording for {}", seconds, model);
                    stop();
                }
            }
        } catch (ProcessExitedUncleanException e) {
            LOG.error("hlsdl exited unclean", e);
        }
        return this;
    }

    private void startHlsdlProcess() {
        try {
            String[] cmdline = prepareCommandLine();
            hlsdl = new Hlsdl.Builder()
                    .logOutput(config.getSettings().loghlsdlOutput)
                    .onStarted(p -> hlsdlProcess = p)
                    .build();
            hlsdl.exec(cmdline, OS.getEnvironment(), targetFile.getParentFile());
        } catch (Exception e) {
            LOG.error("Error starting hlsdl", e);
        }
    }

    private String[] prepareCommandLine() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        String playlistUrl = getSegmentPlaylistUrl(model);
        Map<String, String> headers = model.getHttpHeaderFactory().createSegmentPlaylistHeaders();
        String[] cmdline = new String[9 + headers.size() * 2];
        int idx = 0;
        cmdline[idx++] = config.getSettings().hlsdlExecutable;
        cmdline[idx++] = "-c";
        cmdline[idx++] = "-r";
        cmdline[idx++] = "3";
        cmdline[idx++] = "-w";
        cmdline[idx++] = "3";
        for (Entry<String, String> header : headers.entrySet()) {
            cmdline[idx++] = "-h";
            cmdline[idx++] = header.getKey() + ": " + header.getValue();
        }
        cmdline[idx++] = "-o";
        cmdline[idx++] = targetFile.getCanonicalPath();
        cmdline[idx] = playlistUrl;
        return cmdline;
    }

    protected String getSegmentPlaylistUrl(Model model) throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        LOG.debug("{} stream idx: {}", model.getName(), model.getStreamUrlIndex());
        List<StreamSource> streamSources = model.getStreamSources();
        Collections.sort(streamSources);
        for (StreamSource streamSource : streamSources) {
            LOG.debug("{} src {}", model.getName(), streamSource);
        }
        String url = null;
        if (model.getStreamUrlIndex() >= 0 && model.getStreamUrlIndex() < streamSources.size()) {
            // TODO don't use the index, but the bandwidth. if the bandwidth does not match, take the closest one
            LOG.debug("{} selected {}", model.getName(), streamSources.get(model.getStreamUrlIndex()));
            url = streamSources.get(model.getStreamUrlIndex()).getMediaPlaylistUrl();
        } else {
            // filter out stream resolutions, which are out of range of the configured min and max
            int minRes = Config.getInstance().getSettings().minimumResolution;
            int maxRes = Config.getInstance().getSettings().maximumResolution;
            List<StreamSource> filteredStreamSources = streamSources.stream()
                    .filter(src -> src.getHeight() == 0 || src.getHeight() == UNKNOWN || minRes <= src.getHeight())
                    .filter(src -> src.getHeight() == 0 || src.getHeight() == UNKNOWN || maxRes >= src.getHeight())
                    .toList();

            if (filteredStreamSources.isEmpty()) {
                throw new ExecutionException(new NoStreamFoundException("No stream left in playlist"));
            } else {
                LOG.debug("{} selected {}", model.getName(), filteredStreamSources.get(filteredStreamSources.size() - 1));
                url = filteredStreamSources.get(filteredStreamSources.size() - 1).getMediaPlaylistUrl();
            }
        }
        LOG.debug("Segment playlist url {}", url);
        return url;
    }

    @Override
    public void stop() {
        if (running) {
            running = false;
            if (hlsdlProcess != null) {
                hlsdlProcess.destroy();
                if (hlsdlProcess.isAlive()) {
                    LOG.info("hlsdl didn't terminate. Destroying the process with force!");
                    hlsdlProcess.destroyForcibly();
                    hlsdlProcess = null;
                }
            }
        }
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
    public Model getModel() {
        return model;
    }

    @Override
    public void finalizeDownload() {
        // nothing to do
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
