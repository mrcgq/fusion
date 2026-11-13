package ctbrec.sites.showup;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.HttpClient;
import ctbrec.recorder.download.AbstractDownload;
import ctbrec.recorder.download.RecordingProcess;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;

public class ShowupWebrtcDownload extends AbstractDownload {

    private static final Logger LOG = LoggerFactory.getLogger(ShowupWebrtcDownload.class);
    private static final int MAX_SECONDS_WITHOUT_TRANSFER = 20;

    private final HttpClient httpClient;
    private WebSocket ws;
    private FileOutputStream fout;
    private Instant timeOfLastTransfer = Instant.MAX;

    private volatile boolean running;
    private volatile boolean started;


    private File targetFile;

    public ShowupWebrtcDownload(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        this.config = config;
        this.model = model;
        this.startTime = startTime;
        this.downloadExecutor = executorService;
        splittingStrategy = initSplittingStrategy(config.getSettings());
        targetFile = config.getFileForRecording(model, "mp4", startTime);
        timeOfLastTransfer = Instant.now();
    }

    @Override
    public void stop() {
        running = false;
        if (ws != null) {
            ws.close(1000, "");
            ws = null;
        }
    }

    @Override
    public void finalizeDownload() {
        if (fout != null) {
            try {
                LOG.debug("Closing recording file {}", targetFile);
                fout.close();
            } catch (IOException e) {
                LOG.error("Error while closing recording file {}", targetFile, e);
            }
        }
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
        if (!started) {
            started = true;
            startDownload();
        }

        if (splittingStrategy.splitNecessary(this)) {
            stop();
            rescheduleTime = Instant.now();
        } else {
            rescheduleTime = Instant.now().plusSeconds(5);
        }
        if (!model.isOnline(true)) {
            stop();
        }
        if (Duration.between(timeOfLastTransfer, Instant.now()).getSeconds() > MAX_SECONDS_WITHOUT_TRANSFER) {
            LOG.info("No video data received for {} seconds. Stopping recording for model {}", MAX_SECONDS_WITHOUT_TRANSFER, model);
            stop();
        }
        return this;
    }

    private void startDownload() throws IOException {
        ShowupModel showupModel = (ShowupModel) model;
        Request request = new Request.Builder()
                .url(showupModel.getWebRtcUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "pl")
                .header(ORIGIN, Showup.BASE_URL)
                .build();

        running = true;
        LOG.debug("Opening webrtc connection {}", request.url());
        ws = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                LOG.trace("onOpen {} {}", webSocket, response);
                response.close();
                try {
                    LOG.debug("Recording video stream to {}", targetFile);
                    Files.createDirectories(targetFile.getParentFile().toPath());
                    fout = new FileOutputStream(targetFile);
                } catch (Exception e) {
                    LOG.error("Couldn't open file {} to save the video stream", targetFile, e);
                    stop();
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
                timeOfLastTransfer = Instant.now();
                try {
                    byte[] videoData = bytes.toByteArray();
                    fout.write(videoData);
                    BandwidthMeter.add(videoData.length);
                } catch (IOException e) {
                    if (running) {
                        LOG.error("Couldn't write video stream to file", e);
                        stop();
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                LOG.trace("onMessageT {} {}", webSocket, text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                stop();
                if (t instanceof EOFException) {
                    LOG.info("End of stream detected for model {}", model);
                } else {
                    LOG.error("Websocket failure for model {} {}", model, response, t);
                }
                if (response != null) {
                    response.close();
                }
            }

            @Override
            public void onClosing(WebSocket webSocket, int code, String reason) {
                super.onClosing(webSocket, code, reason);
                LOG.trace("Websocket closing for model {} {} {}", model, code, reason);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                LOG.debug("Websocket closed for model {} {} {}", model, code, reason);
                stop();
            }
        });
    }
}
