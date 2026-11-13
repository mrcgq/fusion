package ctbrec.recorder.download.hls;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.OS;
import ctbrec.Recording;
import ctbrec.io.HttpClient;
import ctbrec.recorder.FFmpeg;
import ctbrec.recorder.SimplifiedLocalRecorder;
import ctbrec.recorder.download.ProcessExitedUncleanException;
import ctbrec.recorder.download.hls.SegmentPlaylist.Segment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

public class MergedFfmpegHlsDownload extends AbstractHlsDownload {

    private static final Logger LOG = LoggerFactory.getLogger(MergedFfmpegHlsDownload.class);
    
    protected File targetFile;
    protected FFmpeg ffmpeg;
    protected Process ffmpegProcess;
    protected OutputStream ffmpegStdIn;
    protected BlockingQueue<Future<SegmentDownload>> queue = new LinkedBlockingQueue<>();
    protected Thread segmentWriterThread;
    protected ReentrantLock conditionLock = new ReentrantLock();
    protected Condition queueNotEmpty = conditionLock.newCondition();
    
    
    public String getStats() {
        String text = (running ? "RUN" : "stp") 
                    + String.format(" lag=%6dms %d/%d: ", 
                                    Duration.between(lastSegmentDownload, Instant.now()).toMillis(), 
                                    queue.size(),
                                    queue.size()+queue.remainingCapacity());
        for (var elem : queue) {
            text += elem.isDone() ? "|" : "-";
        }
        return text;
    }

    public MergedFfmpegHlsDownload(HttpClient client) {
        super(client);
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        LOG.debug("init start");
        super.init(config, model, startTime, executorService);
        String fileSuffix = config.getSettings().ffmpegFileSuffix;
        targetFile = config.getFileForRecording(model, fileSuffix, startTime);

        createTargetDirectory();
        startFfmpegProcess(targetFile);
        if (ffmpegProcess == null) {
            throw new ProcessExitedUncleanException("Couldn't spawn FFmpeg");
        }
        
        segmentWriterThread = Thread.ofVirtual()
            .name("SegmentWriter-" + model.getDisplayName())
            .start(() -> streamSegmentDataToFfmpeg());
    }

    @Override
    public AbstractHlsDownload call() throws Exception {
        super.call();
        try {
            if (!ffmpegProcess.isAlive()) {
                running = false;
                int exitValue = ffmpegProcess.exitValue();
                ffmpeg.shutdown(exitValue);
            }
        } catch (ProcessExitedUncleanException e) {
            LOG.error("FFmpeg exited unclean", e);
        }

        return this;
    }

    protected void streamSegmentDataToFfmpeg() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                try {
                    conditionLock.lock();
                    while (queue.isEmpty()) 
                        queueNotEmpty.await();
                } finally {
                    conditionLock.unlock();
                }
                
                // use peek() instead of take() so that we can look at first element from other places (currently for debug statistics)
                // only take finished (downloaded or failed) elements
                Future<SegmentDownload> future = queue.peek();
                SegmentDownload segmentDownload = future.get();
                queue.take();
                
                if (segmentDownload.isFailed()) {
                    throw segmentDownload.getException();
                }
                
                ByteArrayOutputStream downloadData = (ByteArrayOutputStream) segmentDownload.getOutputStream();
                downloadData.writeTo(ffmpegStdIn);
                lastSegmentDownload = Instant.now();
            } catch (InterruptedException e) {
                LOG.trace("Segment download interrupted for model {}", model, e);
                Thread.currentThread().interrupt();                    
            } catch (Exception e) {
                LOG.error("Segment download failed for model {}", model, e);
            }
        }
    }

    @Override
    public File getTarget() {
        return targetFile;
    }

    @Override
    public void finalizeDownload() {
        internalStop();
    }

    protected void startFfmpegProcess(File target) {
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
    protected void execute(SegmentDownload segmentDownload) {
        try {
            queue.add(segmentDownloadService.submit(segmentDownload));
            
            // notify the writer thread that we have added an element
            try {
                conditionLock.lock();
                queueNotEmpty.signal();
            } finally {
                conditionLock.unlock();
            }
        } catch (Exception e) {
            SimplifiedLocalRecorder.STATS.errorCount++;
            throw e;
        }
        LOG.trace("Enqueuing segment for file {}", targetFile);
    }

    @Override
    public void stop() {
        if (running) {
            internalStop();
        }
    }

    @Override
    protected synchronized void internalStop() {
        running = false;
        
        segmentWriterThread.interrupt();
        try {
            segmentWriterThread.join();
        } catch (InterruptedException e) {
        }
        
        for (Future<SegmentDownload> future : queue) {
            future.cancel(true);
        }
        queue.clear();

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

    @Override
    public String getPath(Model model) {
        String absolutePath = targetFile.getAbsolutePath();
        String recordingsDir = Config.getInstance().getSettings().recordingsDir;
        String relativePath = absolutePath.replaceFirst(Pattern.quote(recordingsDir), "");
        return relativePath;
    }

    @Override
    public void postProcess(Recording recording) {
        // nothing to do
    }

    @Override
    public boolean isSingleFile() {
        return true;
    }

    @Override
    public long getSizeInByte() {
        return getTarget().length();
    }

    protected void createTargetDirectory() throws IOException {
        Files.createDirectories(targetFile.getParentFile().toPath());
    }

    @Override
    protected OutputStream getSegmentOutputStream(Segment segment) {
        return new ByteArrayOutputStream();
    }
}
