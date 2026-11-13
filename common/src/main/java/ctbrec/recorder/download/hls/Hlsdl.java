package ctbrec.recorder.download.hls;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import ctbrec.io.BandwidthMeter;
import ctbrec.io.DevNull;
import ctbrec.io.ProcessStreamRedirector;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.recorder.download.ProcessExitedUncleanException;
import lombok.Data;

public class Hlsdl {

    private static final Logger LOG = LoggerFactory.getLogger(Hlsdl.class);

    // private static ScheduledExecutorService processOutputReader = Executors.newScheduledThreadPool(2, createThreadFactory("hlsdl output stream reader"));
    
    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();

    private Process process;
    private boolean logOutput = false;
    private Consumer<Process> startCallback;
    private Consumer<Integer> exitCallback;
    private File processLog = null;
    private OutputStream processLogStream;
    // private ProcessStreamRedirector stdoutRedirector;
    // private ProcessStreamRedirector stderrRedirector;
    private Thread streamReaderThread;

    private Hlsdl() {}

    private static ThreadFactory createThreadFactory(String name) {
        return r -> {
            Thread t = new Thread(r);
            t.setName(name);
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            return t;
        };
    }

    public void exec(String[] cmdline, String[] env, File executionDir) throws IOException {
        LOG.debug("hlsdl command line: {}", Arrays.toString(cmdline));
        // process = Runtime.getRuntime().exec(cmdline, env, executionDir);
        var builder = new ProcessBuilder(cmdline).directory(executionDir);
        builder.redirectErrorStream(true);
        process = builder.start();
        setupLogging();
        notifyStartCallback(process);
    }


    public void shutdown(int exitCode) throws IOException {
        LOG.debug("hlsdl exit code was {}", exitCode);
        processLogStream.flush();
        processLogStream.close();
        streamReaderThread.interrupt();
        try {
            streamReaderThread.join();
        } catch (InterruptedException e) {
            LOG.warn("interrupted while joining stream reader thread: {}", e);
        }
        // stdoutRedirector.setKeepGoing(false);
        // stderrRedirector.setKeepGoing(false);
        notifyExitCallback(exitCode);
        if (exitCode != 1) {
            if (processLog != null && processLog.exists()) {
                Files.delete(processLog.toPath());
            }
        } else {
            throw new ProcessExitedUncleanException("hlsdl exit code was " + exitCode);
        }
    }

    private void setupLogging() throws IOException {
        if (logOutput) {
            if (processLog == null) {
                processLog = File.createTempFile("hlsdl_", ".log");
            }
            LOG.debug("Logging hlsdl output to {}", processLog);
            processLog.deleteOnExit();
            processLogStream = new FileOutputStream(processLog);
        } else {
            processLogStream = new DevNull();
        }
        // stdoutRedirector = new ProcessStreamRedirector(processOutputReader, process.getInputStream(), processLogStream);
        // stderrRedirector = new ProcessStreamRedirector(processOutputReader, process.getErrorStream(), processLogStream);
        // processOutputReader.submit(stdoutRedirector);
        // processOutputReader.submit(stderrRedirector);
        
        streamReaderThread = Thread.ofVirtual()
            .name("hlsdl output stream reader")
            .start(() -> {
            var w = new OutputStreamWriter(processLogStream);
            long lastDownloadSize = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    var line = process.inputReader().readLine();
                    
                    if (line == null) {
                        LOG.trace("stream ended");
                        w.close();
                        break;
                    }
                    if (line.length() == 0) continue;
                    
                    // parse API messages in JSON format
                    if (line.startsWith("{")) {
                        try {
                            var stats = mapper.readValue(line, ApiStats.class);
                            BandwidthMeter.add(stats.downloadSize - lastDownloadSize);
                            lastDownloadSize = stats.downloadSize;
                        } catch (JsonMappingException e) {
                        }
                    }
                    
                    w.write(line);
                    w.write('\n');
                }
            } catch (IOException e) {
                if (process.isAlive())
                    LOG.debug("Error in stream while process is still alive {}", e);                
            }
        });
    }

    private void notifyStartCallback(Process process) {
        try {
            startCallback.accept(process);
        } catch(Exception e) {
            LOG.error("Exception in onStart callback", e);
        }
    }

    private void notifyExitCallback(int exitCode) {
        try {
            exitCallback.accept(exitCode);
        } catch(Exception e) {
            LOG.error("Exception in onExit callback", e);
        }
    }

    public int waitFor() throws InterruptedException {
        int exitCode = process.waitFor();
        try {
            shutdown(exitCode);
        } catch (IOException e) {
            LOG.error("Error while shutting down hlsdl process", e);
        }
        return exitCode;
    }

    public static class Builder {
        private boolean logOutput = false;
        private File logFile;
        private Consumer<Process> startCallback;
        private Consumer<Integer> exitCallback;

        public Builder logOutput(boolean logOutput) {
            this.logOutput = logOutput;
            return this;
        }

        public Builder logFile(File logFile) {
            this.logFile = logFile;
            return this;
        }

        public Builder onStarted(Consumer<Process> callback) {
            this.startCallback = callback;
            return this;
        }

        public Builder onExit(Consumer<Integer> callback) {
            this.exitCallback = callback;
            return this;
        }

        public Hlsdl build() {
            Hlsdl instance = new Hlsdl();
            instance.logOutput = logOutput;
            instance.startCallback = startCallback != null ? startCallback : p -> {};
            instance.exitCallback = exitCallback != null ? exitCallback : exitCode -> {};
            instance.processLog = logFile;
            return instance;
        }
    }
    
    @Data
    private static class ApiError {
        @JsonProperty("error_code")
        int errorCode;
        @JsonProperty("error_msg")
        String errorMsg;
    }
    
    @Data
    private static class ApiDownloadType {
        // d_t - download type
        @JsonProperty("d_t")
        String downloadType;
    }
    
    @Data
    private static class ApiStats {
        // t_d - total duration, d_d  - download duration, d_s - download size
        @JsonProperty("t_d")
        long totalDuration;
        @JsonProperty("d_d")
        long downloadDuration;
        @JsonProperty("d_s")
        long downloadSize;
    }

}
