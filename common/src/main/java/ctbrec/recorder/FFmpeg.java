package ctbrec.recorder;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

import ctbrec.recorder.download.ProcessExitedUncleanException;

@Slf4j
public class FFmpeg {
    private Process process;
    private boolean logOutput = false;
    private Consumer<Process> startCallback;
    private Consumer<Integer> exitCallback;
    private File ffmpegLog = null;

    private FFmpeg() {}

    public void exec(String[] cmdline, String[] env, File executionDir) throws IOException {
        log.trace("FFmpeg command line: {}", Arrays.toString(cmdline));
        // process = Runtime.getRuntime().exec(cmdline, env, executionDir);
        
        var builder = new ProcessBuilder(cmdline);
        // builder.environment().();
        builder.directory(executionDir);
        setupLogging(builder);
        process = builder.start();
        
        notifyStartCallback(process);
    }


    public void shutdown(int exitCode) throws IOException {
        process.destroy();
        log.trace("FFmpeg exit code was {}", exitCode);
        notifyExitCallback(exitCode);
        if (exitCode != 1) {
            if (ffmpegLog != null && ffmpegLog.exists()) {
                Files.delete(ffmpegLog.toPath());
            }
        } else {
            throw new ProcessExitedUncleanException("FFmpeg exit code was " + exitCode);
        }
    }

    private void setupLogging(ProcessBuilder builder) throws IOException {
        if (logOutput) {
            if (ffmpegLog == null) {
                ffmpegLog = File.createTempFile("ffmpeg_", ".log");
            }
            log.trace("Logging FFmpeg output to {}", ffmpegLog);
            ffmpegLog.deleteOnExit();
            
            builder.redirectOutput(Redirect.to(ffmpegLog));
            builder.redirectErrorStream(true);
        } else {
            builder.redirectOutput(Redirect.DISCARD);
            builder.redirectError(Redirect.DISCARD);
        }
    }

    private void notifyStartCallback(Process process) {
        try {
            startCallback.accept(process);
        } catch(Exception e) {
            log.error("Exception in onStart callback", e);
        }
    }

    private void notifyExitCallback(int exitCode) {
        try {
            exitCallback.accept(exitCode);
        } catch(Exception e) {
            log.error("Exception in onExit callback", e);
        }
    }

    public int waitFor() throws InterruptedException {
        int exitCode = process.waitFor();
        try {
            shutdown(exitCode);
        } catch (IOException e) {
            log.error("Error while shutting down FFmpeg process", e);
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

        public FFmpeg build() {
            FFmpeg instance = new FFmpeg();
            instance.logOutput = logOutput;
            instance.startCallback = startCallback != null ? startCallback : p -> {};
            instance.exitCallback = exitCallback != null ? exitCallback : exitCode -> {};
            instance.ffmpegLog = logFile;
            return instance;
        }
    }

}
