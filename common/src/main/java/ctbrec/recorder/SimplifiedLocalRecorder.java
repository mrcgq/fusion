package ctbrec.recorder;

import com.google.common.eventbus.Subscribe;

import ctbrec.*;
import ctbrec.Recording.State;
import ctbrec.event.*;
import ctbrec.io.HttpClient;
import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.io.json.mapper.PostProcessorMapper;
import ctbrec.notes.LocalModelNotesService;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.postprocessing.PostProcessingContext;
import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.sites.Site;
import ctbrec.sites.SiteUtil;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import com.google.common.collect.Ordering;

import static ctbrec.Recording.State.WAITING;
import static ctbrec.SubsequentAction.*;
import static ctbrec.event.Event.Type.MODEL_ONLINE;
import static java.lang.Thread.MAX_PRIORITY;
import static java.lang.Thread.MIN_PRIORITY;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

@Slf4j
public class SimplifiedLocalRecorder implements Recorder {
    public static final Statistics STATS = new Statistics();
    
    public static final boolean IGNORE_CACHE = true;
    private final List<Model> models = Collections.synchronizedList(new ArrayList<>());
    private final Config config;
    private volatile boolean running;
    private volatile boolean shuttingDown = false;
    private final ReentrantLock recorderLock = new ReentrantLock();
    private final ReentrantLock modelGroupLock = new ReentrantLock();
    private final RecorderHttpClient client;
    private final List<Recording> recordingProcesses = Collections.synchronizedList(new ArrayList<>());
    private final RecordingManager recordingManager;
    private final RecordingPreconditions preconditions;

    
    // thread pools for downloads and post-processing
    private final ExecutorService segmentDownloadPool = Executors.newVirtualThreadPerTaskExecutor();
    private final ExecutorService recordingLoopPool = Executors.newVirtualThreadPerTaskExecutor();
    private final ThreadPoolExecutor postProcessing;
    private final Thread maintenanceThread;
    private long lastSpaceCheck;
    
    Timer saveConfigTimer = new Timer();
    TimerTask saveConfigTask = null;
    

    public SimplifiedLocalRecorder(Config config, List<Site> sites) throws IOException {
        this.config = config;
        client = new RecorderHttpClient(config);
        recordingManager = new RecordingManager(config, sites);
        loadModels(sites);
        int ppThreads = config.getSettings().postProcessingThreads;
        BlockingQueue<Runnable> ppQueue = new LinkedBlockingQueue<>();
        postProcessing = new ThreadPoolExecutor(ppThreads, ppThreads, 5, TimeUnit.MINUTES, ppQueue, createThreadFactory("PP", MIN_PRIORITY));
        
        running = true;
        registerEventBusListener();

        preconditions = new RecordingPreconditions(this, config);

        log.debug("Recorder initialized");
        log.info("Models to record: {}", models);
        log.info("Saving recordings in {}", config.getSettings().recordingsDir);

        maintenanceThread = startMaintenanceLoop();
    }

    private Thread startMaintenanceLoop() {
        var t = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {                
                checkFreeSpace();
                //threadPoolScaler.tick();
                waitABit(1000);
            }
        });
        t.setName("Recording loop");
        t.start();
        return t;
    }

    private void checkFreeSpace() {
        if ((System.currentTimeMillis() - lastSpaceCheck) > 0) {
            lastSpaceCheck = System.currentTimeMillis();
            try {
                if (!recordingProcesses.isEmpty() && notEnoughSpaceForRecording()) {
                    log.info("No space left -> Stopping all recordings");
                    stopRecordingProcesses();
                    EventBusHolder.BUS.post(new NoSpaceLeftEvent());
                }
            } catch (IOException e) {
                log.error("Couldn't check space left on device", e);
            }
        }
    }

    private void waitABit(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting in main loop. CPU usage might be high now :(");
        }
    }

    private void singleRecordingLoop(Recording recording) {
        while (recording.getRecordingProcess().isRunning()) {
            try {
                // run single iteration
                recording.getRecordingProcess().call();
                
                if (recording.getModel().isSuspended()) {
                    log.info("Recording process for suspended model found: {}. Stopping now", recording.getModel());
                    stopRecordingProcess(recording);
                    submitPostProcessingJob(recording);
                    break;
                }
                
                // wait necessary time
                Instant rescheduleAt = recording.getRecordingProcess().getRescheduleTime();
                Duration duration = Duration.between(Instant.now(), rescheduleAt);
                long delayInMillis = Math.max(0, duration.toMillis());
                log.trace("Current iteration is done {}. Recording status {}. Rescheduling in {}ms", recording.getModel().getName(), recording.getStatus().name(), delayInMillis);
                Thread.sleep(delayInMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                fail(recording);
                return;
            } catch (Exception e) {
                log.error("Error while recording model {}. Stopping recording.", recording.getModel(), e);
                fail(recording);
                return;
            }
        } 
        
        removeRecordingProcess(recording);
        if (deleteIfEmpty(recording)) {
            return;
        }
        submitPostProcessingJob(recording);
        tryRestartRecording(recording.getModel());
    }

    private void removeRecordingProcess(Recording rec) {
        recorderLock.lock();
        try {
            recordingProcesses.remove(rec);
        } finally {
            recorderLock.unlock();
        }
    }

    private void fail(Recording recording) {
        stopRecordingProcess(recording);
        recording.getRecordingProcess().finalizeDownload();
        if (deleteIfEmpty(recording)) {
            return;
        }
        submitPostProcessingJob(recording);
        tryRestartRecording(recording.getModel());
    }

    private void loadModels(List<Site> sites) {
        config.getSettings().models
                .stream()
                .map(Mappers.getMapper(ModelMapper.class)::toModel)
                .forEach(m -> {
                    SiteUtil.getSiteForModel(sites, m).ifPresent(m::setSite);
                    if (m.getSite() != null) {
                        if (m.getSite().isEnabled()) {
                            models.add(m);
                        } else {
                            log.info("{} disabled -> ignoring {}", m.getSite().getName(), m.getName());
                        }
                    } else {
                        log.info("Site for model {} is unknown -> ignoring", m.getName());
                    }
                });
    }

    private void shutdownPool(String name, ExecutorService executorService, int secondsToWaitForTermination) throws InterruptedException {
        log.info("Stopping thread pool {}", name);
        executorService.shutdown();
        boolean terminated = executorService.awaitTermination(secondsToWaitForTermination, TimeUnit.SECONDS);
        if (terminated) {
            log.info("Thread pool {} terminated", name);
        } else {
            log.warn("{} did not terminate in time.", name);
        }
    }

    private void stopRecordings() {
        log.info("Stopping all recordings");
        recorderLock.lock();
        try {
            for (Recording recording : recordingProcesses) {
                recording.getRecordingProcess().stop();
            }
            for (Recording recording : recordingProcesses) {
                recording.getRecordingProcess().awaitEnd();
            }
        } finally {
            recorderLock.unlock();
        }
        waitForRecordingsToTerminate();
        log.info("Recordings have been stopped");
    }

    private void waitForRecordingsToTerminate() {
        long secondsToWait = TimeUnit.MINUTES.toSeconds(10);
        for (int i = 0; i < secondsToWait; i++) {
            if (recordingProcesses.isEmpty()) {
                return;
            }

            log.info("Waiting for recording processes to terminate");
            waitABit(1000);
        }
        log.warn("Recording processes didn't finish in {} seconds. Continuing, but some recordings might not get post-processed", secondsToWait);
    }

    private void submitPostProcessingJob(Recording recording) {
        setRecordingStatus(recording, WAITING);
        try {
            postProcessing.submit(() -> {
                try {
                    runPostProcessing(recording);
                } catch (Exception e) {
                    if (e instanceof InterruptedException) { // NOSONAR
                        Thread.currentThread().interrupt();
                    }
                    log.error("Error while post-processing recording {}", recording, e);
                    recording.setStatus(State.FAILED);
                    try {
                        recordingManager.saveRecording(recording);
                    } catch (IOException e1) {
                        log.error("Couldn't update recording state for recording {}", recording, e1);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            log.error("Could not start post-processing for {} {}:{}. Execution rejected by thread pool", recording, recording.getModel().getSite().getName(), recording.getModel().getDisplayName());
        }
        log.trace("submitPostProcessingJob(): {}", postProcessing);
    }

    private void runPostProcessing(Recording recording) throws IOException, InterruptedException {
        setRecordingStatus(recording, State.POST_PROCESSING);
        recording.getRecordingProcess().stop();
        recording.getRecordingProcess().awaitEnd();
        recording.getRecordingProcess().finalizeDownload();
        recording.refresh();
        recordingManager.saveRecording(recording);
        recording.postprocess();
        List<PostProcessor> postProcessors = config.getSettings().postProcessors
                .stream()
                .map(Mappers.getMapper(PostProcessorMapper.class)::toPostProcessor)
                .toList();
        PostProcessingContext ctx = createPostProcessingContext(recording);
        for (PostProcessor postProcessor : postProcessors) {
            if (postProcessor.isEnabled()) {
                log.debug("Running post-processor: {}", postProcessor.getName());
                boolean continuePP = postProcessor.postprocess(ctx);
                if (!continuePP) {
                    break;
                }
            } else {
                log.debug("Skipping post-processor {} because it is disabled", postProcessor.getName());
            }
        }
        recording.refresh();
        if (recording.getStatus() != State.DELETED) {
            setRecordingStatus(recording, State.FINISHED);
            recordingManager.saveRecording(recording);
        }
        log.info("Post-processing finished for {}", recording.getModel().getName());
    }

    private PostProcessingContext createPostProcessingContext(Recording recording) {
        PostProcessingContext ctx = new PostProcessingContext();
        ctx.setConfig(config);
        ctx.setRecorder(this);
        ctx.setRecording(recording);
        ctx.setRecordingManager(recordingManager);
        ctx.setModelNotesService(new LocalModelNotesService(config));
        return ctx;
    }

    private void setRecordingStatus(Recording recording, State status) {
        recording.setStatus(status);
        RecordingStateChangedEvent evt = new RecordingStateChangedEvent(recording.getRecordingProcess().getTarget(), status, recording.getModel(),
                recording.getStartDate());
        EventBusHolder.BUS.post(evt);
    }

    @Override
    public void addModel(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        Optional<Model> result = findModel(model);
        if (result.isEmpty()) {
            if (config.isIgnored(model)) {
                throw new ModelIsIgnoredException(model);
            }

            log.info("Model {} added", model);
            recorderLock.lock();
            try {
                models.add(model);
                if (Objects.equals(model.getAddedTimestamp(), Instant.EPOCH)) {
                    model.setAddedTimestamp(Instant.now());
                }
                config.getSettings().models.add(Mappers.getMapper(ModelMapper.class).toDto(model));
                config.save();
            } catch (IOException e) {
                errorSavingConfig(e);
            } finally {
                recorderLock.unlock();
            }
            startRecordingProcessAsync(model);
        } else {
            Model existing = result.get();
            copyModelProperties(model, existing);
            startRecordingProcessAsync(existing);
        }
    }

    private void errorSavingConfig(IOException e) {
        log.error("Couldn't save config", e);
    }

    private void copyModelProperties(Model src, Model existing) {
        existing.setSuspended(src.isSuspended());
        existing.setForcePriority(src.isForcePriority());
        existing.setMarkedForLaterRecording(src.isMarkedForLaterRecording());
        existing.setPriority(src.getPriority());
        existing.setRecordUntil(src.getRecordUntil());
        existing.setRecordUntilSubsequentAction(src.getRecordUntilSubsequentAction());
    }

    private RecordingProcess createDownload(Model model) throws IOException {
        RecordingProcess download = model.createDownload();
        download.init(config, model, Instant.now(), segmentDownloadPool);
        Objects.requireNonNull(download.getStartTime(),
                "At this point the download should have set a startTime. Make sure to set a startTime in " + download.getClass() + ".init()");
        log.debug("Downloading with {}", download.getClass().getSimpleName());
        return download;
    }

    private void executeRecordUntilSubsequentAction(Model model) {
        log.debug("Stopping recording {} because the recording timeframe ended at {}. Subsequent action is {}", model,
                model.getRecordUntil().atZone(ZoneId.systemDefault()), model.getRecordUntilSubsequentAction());
        if (model.getRecordUntilSubsequentAction() == PAUSE) {
            model.setSuspended(true);
        } else if (model.getRecordUntilSubsequentAction() == REMOVE) {
            try {
                stopRecording(model);
            } catch (Exception e1) {
                log.error("Error while stopping recording", e1);
            }
        } else if (model.getRecordUntilSubsequentAction() == RECORD_LATER) {
            try {
                markForLaterRecording(model, true);
            } catch (Exception e1) {
                log.error("Error while stopping recording", e1);
            }
        }
        // reset values, so that model can be recorded again
        model.setRecordUntil(null);
        model.setRecordUntilSubsequentAction(PAUSE);
    }

    private Recording createRecording(RecordingProcess download) throws IOException {
        Model model = download.getModel();
        Recording rec = new Recording(config.getSettings().recordingsDir);
        rec.setId(UUID.randomUUID().toString());
        rec.setRecordingProcess(download);
        String recordingFile = download.getPath(model).replace('\\', '/');
        File absoluteFile = new File(config.getSettings().recordingsDir, recordingFile);
        rec.setAbsoluteFile(absoluteFile);
        rec.setModel(model);
        rec.setStartDate(download.getStartTime());
        rec.setSingleFile(download.isSingleFile());
        recordingProcesses.add(rec);
        recordingManager.add(rec);
        return rec;
    }

    private boolean deleteIfEmpty(Recording rec) {
        boolean deleted = false;
        try {
            rec.refresh();
            long sizeInByte = rec.getSizeInByte();
            if (sizeInByte <= 0) {
                log.info("Deleting empty recording {}", rec);
                delete(rec);
                deleted = true;
            }
            setRecordingStatus(rec, deleted ? State.DELETED : WAITING);
            if (!deleted) {
                // only save the status, if the recording has not been deleted, otherwise we recreate the metadata file
                recordingManager.saveRecording(rec);
            }
        } catch (IOException e) {
            log.error("Couldn't execute post-processing step \"delete if empty\"", e);
        }
        return deleted;
    }

    @Override
    public void stopRecording(Model model) throws IOException {
        recorderLock.lock();
        try {
            if (models.contains(model)) {
                models.remove(model);
                saveConfig();
                log.info("Model {} removed", model);
            } else {
                throw new NoSuchElementException("Model " + model.getName() + " [" + model.getUrl() + "] not found in list of recorded models");
            }

            if (isRecordingRunningForModel(model)) {
                getRecordingProcessForModel(model).map(Recording::getRecordingProcess).ifPresent(RecordingProcess::stop);
            }
        } finally {
            recorderLock.unlock();
        }
    }

    public boolean isRecordingRunningForModel(Model model) {
        return recordingProcesses.stream().anyMatch(rec -> Objects.equals(rec.getModel(), model));
    }

    public Optional<Recording> getRecordingProcessForModel(Model model) {
        return recordingProcesses.stream().filter(rec -> Objects.equals(rec.getModel(), model)).findAny();
    }

    @Override
    public void switchStreamSource(Model model) throws IOException {
        if (models.contains(model)) {
            int index = models.indexOf(model);
            models.get(index).setStreamUrlIndex(model.getStreamUrlIndex());
            saveConfig();
            log.debug("Switching stream source to index {} for model {}", model.getStreamUrlIndex(), model.getName());
            recorderLock.lock();
            try {
                getRecordingProcessForModel(model).ifPresent(this::stopRecordingProcess);
            } finally {
                recorderLock.unlock();
            }
            tryRestartRecording(model);
        } else {
            log.warn("Couldn't switch stream source for model {}. Not found in list", model.getName());
        }
    }

    void stopRecordingProcess(Recording rec) {
        recorderLock.lock();
        try {
            var model = rec.getModel();
            log.debug("Stopping recording for {} - recording found: {}", model, rec);
            log.debug("Stopping download for {}", model);
            rec.getRecordingProcess().stop();
            recordingProcesses.remove(rec);
        } finally {
            recorderLock.unlock();
        }
    }

    private void stopRecordingProcesses() {
        recorderLock.lock();
        try {
            for (Recording rec : recordingProcesses) {
                rec.getRecordingProcess().stop();
            }
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public List<Model> getModels() {
        recorderLock.lock();
        try {
            return new ArrayList<>(models);
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public List<Recording> getRecordings() {
        return recordingManager.getAll();
    }

    @Override
    public void delete(Recording recording) throws IOException {
        recordingManager.delete(recording);
    }

    @Override
    public void shutdown(boolean immediately) {
        log.info("Shutting down");
        shuttingDown = true;
        maintenanceThread.interrupt();
        if (!immediately) {
            try {
                stopRecordings();
                shutdownPool("Recording loops", recordingLoopPool, 60);
                shutdownPool("SegmentDownloadPool", segmentDownloadPool, 60);
                shutdownPool("Post-Processing", postProcessing, 600);
            } catch (InterruptedException e) {
                log.warn("Interrupted while waiting for recordings to finish");
                Thread.currentThread().interrupt();
            }
        }
        running = false;
    }

    @Override
    public void suspendRecording(Model model) {
        recorderLock.lock();
        try {
            int index = models.indexOf(model);
            if (index != -1) {
                var ourModel = models.get(index);
                ourModel.setSuspended(true);
                model.setSuspended(true);
                saveConfig();
                
                startGroupRecordingOfModel(ourModel);
            } else {
                log.warn("Couldn't suspend model {}. Not found in list", model.getName());
                return;
            }

            getRecordingProcessForModel(model).ifPresent(this::stopRecordingProcess);
        } finally {
            recorderLock.unlock();
        }
    }

    private void saveConfig() {        
        if (saveConfigTask != null)
            saveConfigTask.cancel();
           
        saveConfigTask = new TimerTask() {
            @Override
            public void run() {
                recorderLock.lock();
                try {
                    config.getSettings().models = models.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
                    config.save();
                } catch (IOException e) {
                    errorSavingConfig(e);
                } finally {
                    recorderLock.unlock();
                }
            }
        };
        saveConfigTimer.schedule(saveConfigTask, config.getSettings().configSavingDelayMs);
    }

    @Override
    public void resumeRecording(Model model) throws IOException {
        recorderLock.lock();
        try {
            if (models.contains(model)) {
                int index = models.indexOf(model);
                Model m = models.get(index);
                m.setSuspended(false);
                m.setMarkedForLaterRecording(false);
                model.setSuspended(false);
                model.setMarkedForLaterRecording(false);
                saveConfig();
                startRecordingProcessAsync(m);
            } else {
                log.warn("Couldn't resume model {}. Not found in list", model.getName());
            }
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public void forcePriorityRecording(Model model) throws IOException {
        recorderLock.lock();
        try {
            if (models.contains(model)) {
                int index = models.indexOf(model);
                Model m = models.get(index);
                m.setForcePriority(true);
                m.setMarkedForLaterRecording(false);
                model.setForcePriority(true);
                model.setMarkedForLaterRecording(false);
                saveConfig();
                startRecordingProcessAsync(m);
            } else {
                log.warn("Couldn't force ignore priority for model {}. Not found in list", model.getName());
            }
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public void resumePriorityRecording(Model model) {
        recorderLock.lock();
        try {
            if (models.contains(model)) {
                int index = models.indexOf(model);
                models.get(index).setForcePriority(false);
                model.setForcePriority(false);
                saveConfig();
            } else {
                log.warn("Couldn't resume respecting priority for model {}. Not found in list", model.getName());
                return;
            }

            getRecordingProcessForModel(model).ifPresent(this::stopRecordingProcess);
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public boolean isTracked(Model model) {
        Optional<Model> m = findModel(model);
        boolean markedForRecording = m.map(Model::isMarkedForLaterRecording).orElse(false);
        return m.isPresent() && !markedForRecording;
    }

    @Override
    public boolean isSuspended(Model model) {
        return findModel(model).map(Model::isSuspended).orElse(false);
    }

    @Override
    public boolean isMarkedForLaterRecording(Model model) {
        return findModel(model).map(Model::isMarkedForLaterRecording).orElse(false);
    }

    @Override
    public void markForLaterRecording(Model model, boolean mark) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        Optional<Model> existingModel = findModel(model);
        if (existingModel.isPresent()) {
            Model m = existingModel.get();
            log.debug("Mark for later: {}. Model found: {}", mark, m);
            m.setMarkedForLaterRecording(mark);
            if (mark && getCurrentlyRecording().contains(m)) {
                log.debug("Stopping recording of {}", m);
                getRecordingProcessForModel(m).ifPresent(this::stopRecordingProcess);
            }
            if (!mark) {
                log.debug("Removing model: {}", m);
                stopRecording(model);
            }
        } else {
            if (mark) {
                log.debug("Model {} not found to mark for later recording", model);
                model.setMarkedForLaterRecording(true);
                addModel(model);
            }
        }
        saveConfig();
    }

    private Optional<Model> findModel(Model m) {
        recorderLock.lock();
        try {
            int index = models.indexOf(m);
            if (index >= 0) {
                return Optional.of(models.get(index));
            } else {
                return Optional.empty();
            }
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public List<Model> getOnlineModels() {
        return getModels().stream().filter(m -> {
            try {
                return m.isOnline();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                return false;
            }
        }).toList();
    }

    @Override
    public HttpClient getHttpClient() {
        return client;
    }

    @Override
    public long getTotalSpaceBytes() throws IOException {
        return getRecordingsFileStore().getTotalSpace();
    }

    @Override
    public long getFreeSpaceBytes() throws IOException {
        return getRecordingsFileStore().getUsableSpace();
    }

    private FileStore getRecordingsFileStore() throws IOException {
        File recordingsDir = new File(config.getSettings().recordingsDir);
        if (!recordingsDir.exists()) {
            Files.createDirectories(recordingsDir.toPath());
        }
        FileStore store = Files.getFileStore(recordingsDir.toPath());
        return store;
    }

    boolean notEnoughSpaceForRecording() throws IOException {
        long minimum = config.getSettings().minimumSpaceLeftInBytes;
        if (minimum == 0) { // 0 means don't check
            return getFreeSpaceBytes() <= 100 * 1024 * 1024; // leave at least 100 MiB free
        } else {
            return getFreeSpaceBytes() <= minimum;
        }
    }

    private void tryRestartRecording(Model model) {
        if (!running || shuttingDown) {
            // recorder is not in recording state
            return;
        }

        boolean modelInRecordingList = isTracked(model);

        if (modelInRecordingList) {
            // .isOnline() check does blocking http request, so do this async
            recordingLoopPool.submit(() -> {
                try {
                    boolean online = model.isOnline(IGNORE_CACHE);
                    if (online) {
                        log.info("Restarting recording for model {}", model);
                        online = startRecordingProcessSync(model);
                    } 
                    
                    // if the specific model is offline or starting it failed, try to restart it's group
                    // FIXME: this might spam unnecessary start attempts
                    if (!online) {
                        log.info("Restarting recording for group of offline model {}", model);
                        startGroupRecordingOfModel(model);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Couldn't restart recording for model {}", model);
                } catch (Exception e) {
                    log.error("Couldn't restart recording for model {}", model);
                }
            });
        }
    }

    private void registerEventBusListener() {
        EventBusHolder.BUS.register(new Object() {
            @Subscribe
            public void modelEvent(Event e) {
                recorderLock.lock();
                try {
                    if (e.getType() == MODEL_ONLINE) {
                        ModelIsOnlineEvent evt = (ModelIsOnlineEvent) e;
                        Model model = evt.getModel();
                        log.trace("Model online event: {} - suspended:{} - already recording:{}", model, model.isSuspended(), isRecordingRunningForModel(model));
                        log.trace("Recording processes: {}", recordingProcesses.size());
                        if (!isSuspended(model) && !isRecordingRunningForModel(model)) {
                            startRecordingProcessAsync(model);
                        }
                    }
                } catch (Exception e1) {
                    log.error("Error while handling model state changed event {}", e, e1);
                } finally {
                    recorderLock.unlock();
                }
            }
        });
    }

    private boolean startRecordingProcessSync(Model model) {
        recorderLock.lock();
        try {
            preconditions.check(model);
            log.info("Starting recording for model {}", model.getName());
            RecordingProcess download = createDownload(model);
            Recording rec = createRecording(download);
            setRecordingStatus(rec, State.RECORDING);
            rec.getModel().setLastRecorded(rec.getStartDate());
            recordingManager.saveRecording(rec);
            recordingLoopPool.submit(() -> {singleRecordingLoop(rec);});
            return true;
        } catch (RecordUntilExpiredException e) {
            log.info("Precondition not met to record {}: {}", model, e.getLocalizedMessage());
            executeRecordUntilSubsequentAction(model);
        } catch (PreconditionNotMetException e) {
            log.info("Precondition not met to record {}: {}", model, e.getLocalizedMessage());
        } catch (Exception e) {
            log.error("Couldn't start recording process for {}", model, e);
        } finally {
            recorderLock.unlock();
        }
        return false;
    }

    private void startRecordingProcessAsync(Model model) {
        recordingLoopPool.submit(() -> {
            startRecordingProcessSync(model);
        });
    }

    private ThreadFactory createThreadFactory(String name, int priority) {
        return r -> {
            Thread t = Thread.ofPlatform().unstarted(r);
            t.setName(name + " " + UUID.randomUUID().toString().substring(0, 8));
            t.setDaemon(true);
            t.setPriority(priority);
            return t;
        };
    }

    @Override
    public void rerunPostProcessing(Recording recording) throws IOException {
        recording.setPostProcessedFile(null);
        for (Recording other : recordingManager.getAll()) {
            if (other.equals(recording)) {
                RecordingProcess download = other.getModel().createDownload();
                download.init(Config.getInstance(), other.getModel(), other.getStartDate(), segmentDownloadPool);
                other.setRecordingProcess(download);
                other.setPostProcessedFile(null);
                other.setStatus(WAITING);
                submitPostProcessingJob(other);
                return;
            }
        }
        log.error("Recording {} not found. Can't rerun post-processing", recording);
    }
    
    private boolean startGroupRecordingOfModel(Model model) {                
        var opt = getModelGroup(model);
        if (!opt.isPresent()) 
            return false;
        
        var group = opt.get();
            
        recordingLoopPool.submit(() -> {
            List<Model> groupModels = group.getModelUrls().stream()
                .map(url -> {
                    // FIXME: replace loop with hashmap lookup
                    for (var m : models) {
                        if (m.getUrl().equals(url))
                            return Optional.of(m);
                    }
                    return Optional.empty();
                })
                .filter(Optional::isPresent)
                .map(x -> (Model)x.get())
                .filter(x -> { 
                    try {
                        // this call is IO blocking
                        return x.isOnline(); 
                    } catch (Exception e) {
                        return false;
                    }
                })
                .sorted((l, r) -> Integer.compare(r.getPriority(), l.getPriority()))
                .toList();
                
            for (var groupModel : groupModels) {
                if (startRecordingProcessSync(groupModel))
                    return;
            }
        });
        
        return true;
    }

    @Override
    public void priorityChanged(Model model) {
        recorderLock.lock();
        try {
            int index = models.indexOf(model);
            if (index != -1) {
                var ourModel = models.get(index);
                ourModel.setPriority(model.getPriority()); // TODO: is this needed?
                saveConfig();
                
                startGroupRecordingOfModel(ourModel);
            } else {
                log.warn("Couldn't change priority for model {}. Not found in list", model.getName());
            }
        } finally {
            recorderLock.unlock();
        }
    }

    @Override
    public void pin(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        recordingManager.pin(recording);
    }

    @Override
    public void unpin(Recording recording) throws IOException {
        recordingManager.unpin(recording);
    }

    @Override
    public void setNote(Recording rec, String note) throws IOException {
        recordingManager.setNote(rec, note);
    }

    @Override
    public void stopRecordingAt(Model model) throws IOException {
        recorderLock.lock();
        try {
            int index = models.indexOf(model);
            if (index >= 0) {
                Model m = models.get(index);
                m.setRecordUntil(model.getRecordUntil());
                m.setRecordUntilSubsequentAction(model.getRecordUntilSubsequentAction());
                log.debug("Stopping recording of model {} at {} and then {}", m, model.getRecordUntil(), m.getRecordUntilSubsequentAction());
                saveConfig();
            } else {
                throw new NoSuchElementException("Model " + model.getName() + " [" + model.getUrl() + "] not found in list of recorded models");
            }
        } finally {
            recorderLock.unlock();
        }

        if (Instant.now().isAfter(model.getRecordUntil())) {
            executeRecordUntilSubsequentAction(model);
        }
    }

    boolean isRunning() {
        return running;
    }

    boolean isShuttingDown() {
        return shuttingDown;
    }

    List<Recording> getRecordingProcesses() {
        return recordingProcesses;
    }

    @Override
    public void pause() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        log.info("Pausing recorder");
        try {
            running = false;
            stopRecordingProcesses();
        } catch (Exception e) {
            running = true;
            throw e;
        }
    }

    @Override
    public void resume() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        log.info("Resuming recorder");
        running = true;
    }

    @Override
    public boolean isForcePriority(Model model) {
        return findModel(model).map(Model::isForcePriority).orElse(false);
    }

    @Override
    public int getModelCount() {
        return (int) models.stream().filter(m -> !m.isMarkedForLaterRecording()).count();
    }

    @Override
    public int getGroupedModelCount() {
        Map<String, String> urlToGroup = new HashMap<>();
        for (var group : getModelGroups()) {
            for (var url : group.getModelUrls()) {
                urlToGroup.putIfAbsent(url, group.getName());
            }
        }
        return (int) models.stream().filter(m -> !m.isMarkedForLaterRecording()).map(m -> urlToGroup.getOrDefault(m.getUrl(), m.getName())).distinct().count();
    }

    @Override
    public Set<ModelGroup> getModelGroups() {
        modelGroupLock.lock();
        try {
            return new HashSet<>(config.getSettings().modelGroups);
        } finally {
            modelGroupLock.unlock();
        }
    }

    @Override
    public void saveModelGroup(ModelGroup group) throws IOException {
        Set<ModelGroup> modelGroups = config.getSettings().modelGroups;
        modelGroupLock.lock();
        try {
            modelGroups.remove(group);
            modelGroups.add(group);
            config.save();
        } finally {
            modelGroupLock.unlock();
        }
    }

    @Override
    public void deleteModelGroup(ModelGroup group) throws IOException {
        modelGroupLock.lock();
        try {
            config.getSettings().modelGroups.remove(group);
            config.save();
        } finally {
            modelGroupLock.unlock();
        }
    }
}
