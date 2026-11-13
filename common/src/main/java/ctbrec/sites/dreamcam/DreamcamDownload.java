package ctbrec.sites.dreamcam;

import ctbrec.*;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.HttpClient;
import ctbrec.recorder.FFmpeg;
import ctbrec.recorder.download.AbstractDownload;
import ctbrec.recorder.download.ProcessExitedUncleanException;
import ctbrec.recorder.download.RecordingProcess;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;

public class DreamcamDownload extends AbstractDownload {

    private static final Logger LOG = LoggerFactory.getLogger(DreamcamDownload.class);
    private static final int MAX_SECONDS_WITHOUT_TRANSFER = 30;

    private final HttpClient httpClient;
    private Instant timeOfLastTransfer = Instant.MAX;

    protected File targetFile;
    protected FFmpeg ffmpeg;
    protected Process ffmpegProcess;
    protected OutputStream ffmpegStdIn;
    protected Lock ffmpegStreamLock = new ReentrantLock();
    protected String wsUrl;

    private volatile boolean running;
    private volatile boolean started;
    private final transient Object monitor = new Object();
    private WebSocket ws;
    private DreamcamModel model;

    public DreamcamDownload(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        super.init(config, model, startTime, executorService);
        this.model = (DreamcamModel) model;
        timeOfLastTransfer = startTime;
        String fileSuffix = config.getSettings().ffmpegFileSuffix;
        targetFile = config.getFileForRecording(model, fileSuffix, startTime);
        createTargetDirectory();
        startFfmpegProcess(targetFile);
        if (ffmpegProcess == null) {
            throw new ProcessExitedUncleanException("Couldn't spawn FFmpeg");
        }
    }

    @Override
    public int getSelectedResolution() {
        return 0;
    }

    @Override
    public void stop() {
        if (running) {
            internalStop();
        }
    }

    private synchronized void internalStop() {
        running = false;
        if (ws != null) {
            ws.close(1000, null);
            ws = null;
        }
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
            LOG.error("Error while downloading", e);
            stop();
        }
        if (!model.isOnline()) {
            LOG.debug("Model {} not online. Stop recording.", model);
            stop();
        }
        if (splittingStrategy.splitNecessary(this)) {
            LOG.debug("Split necessary for model {}. Stop recording.", model);
            internalStop();
            rescheduleTime = Instant.now();
        } else {
            rescheduleTime = Instant.now().plusSeconds(5);
        }
        if (Duration.between(timeOfLastTransfer, Instant.now()).getSeconds() > MAX_SECONDS_WITHOUT_TRANSFER) {
            LOG.debug("No video data received for {} seconds. Stopping recording for model {}", MAX_SECONDS_WITHOUT_TRANSFER, model);
            stop();
        }
        return this;
    }

    private void startDownload() {
        downloadExecutor.submit(() -> {
            running = true;
            ffmpegStreamLock.lock();
            try {
                wsUrl = model.getWsUrl();
                LOG.debug("{} ws url: {}", model.getName(), wsUrl);
                if (StringUtil.isBlank(wsUrl)) {
                    LOG.error("{}: Stream URL not found", model);
                    stop();
                    return;
                }
                Request request = new Request.Builder()
                        .url(wsUrl)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(ACCEPT, "*/*")
                        .header(ACCEPT_LANGUAGE, "en")
                        .header(ORIGIN, model.getSite().getBaseUrl())
                        .header(REFERER, model.getSite().getBaseUrl() + "/")
                        .build();

                ws = httpClient.newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        super.onOpen(webSocket, response);
                        LOG.debug("{}: Websocket open", model);
                        if (response != null) {
                            response.close();
                        }
                        JSONObject msg = new JSONObject();
                        msg.put("url", "stream/hello");
                        msg.put("version", "0.0.1");
                        webSocket.send(msg.toString());
                    } // onOpen

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        super.onClosed(webSocket, code, reason);
                        LOG.trace("{}: Websocket closed", model);
                        stop();
                        synchronized (monitor) {
                            monitor.notifyAll();
                        }
                    } // onClosed

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        super.onFailure(webSocket, t, response);
                        LOG.debug("{}: Websocket failed: {}", model, t.getMessage());
                        if (response != null) {
                            response.close();
                        }
                        stop();
                        synchronized (monitor) {
                            monitor.notifyAll();
                        }
                    } // onFailure

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        super.onMessage(webSocket, text);
                        LOG.trace("{} ws message: {}", model, text);
                        JSONObject message = new JSONObject(text);
                        if (message.optString("url").equals("stream/qual")) {
                            JSONObject msg = new JSONObject();
                            msg.put("quality", "test");
                            msg.put("url", "stream/play");
                            msg.put("version", "0.0.1");
                            webSocket.send(msg.toString());
                        }
                    } // onMessage

                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        super.onMessage(webSocket, bytes);
                        timeOfLastTransfer = Instant.now();
                        try {
                            if (running) {
                                byte[] videoData = bytes.toByteArray();
                                ffmpegStdIn.write(videoData);
                                BandwidthMeter.add(videoData.length);
                            }
                        } catch (IOException e) {
                            if (running) {
                                LOG.error("Couldn't write video stream to file", e);
                                stop();
                            }
                        }
                    } // onMessage

                }); // websocket

                synchronized (monitor) {
                    try {
                        monitor.wait();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Interrupted while waiting for the download to terminate");
                    }
                }
            } catch (IOException ex) {
                if (running) {
                    LOG.error("Error while downloading: {}", ex.getMessage());
                    stop();
                }
            } finally {
                ffmpegStreamLock.unlock();
                running = false;
            }
        }); // submit
    }

    protected void createTargetDirectory() throws IOException {
        Files.createDirectories(targetFile.getParentFile().toPath());
    }
}
