package ctbrec.recorder;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.Recording;
import ctbrec.recorder.download.RecordingProcess;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Comparators;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

public class RecordingPreconditions {

    private static final Logger LOG = LoggerFactory.getLogger(RecordingPreconditions.class);
    public static final boolean IGNORE_CACHE = true;

    private final Config config;
    private final SimplifiedLocalRecorder recorder;
    private long lastPreconditionMessage = 0;


    RecordingPreconditions(SimplifiedLocalRecorder recorder, Config config) {
        this.recorder = recorder;
        this.config = config;
    }

    void check(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException, ExecutionException {
        LOG.debug("Checking preconditions for model {}", model);
        ensureRecorderIsActive();
        ensureNotInTimeoutPeriod();
        ensureModelIsNotSuspended(model);
        ensureModelIsNotDelayed(model);
        ensureModelIsNotMarkedForLaterRecording(model);
        ensureRecordUntilIsInFuture(model);
        ensureNoRecordingRunningForModel(model);
        ensureModelShouldBeRecorded(model);
        ensureEnoughSpaceForRecording();
        //ensureAllModelsFromGroupAreOnlineChecked(model);
        ensureNoOtherFromModelGroupIsRecording(model);
        ensureModelIsOnline(model);
        ensureDownloadSlotAvailable(model);
    }

    private void ensureNotInTimeoutPeriod() {
        LocalTime start = config.getSettings().timeoutRecordingStartingAt;
        LocalTime end = config.getSettings().timeoutRecordingEndingAt;
        if (start.equals(end)) {
            return;
        }

        LocalTime now = LocalTime.now();
        if (start.isBefore(end) && now.isAfter(start) && now.isBefore(end)) {
            throw new PreconditionNotMetException("Current time is in recording timeout " + start + " - " + end);
        } else if (start.isAfter(end) && !(now.isAfter(end) && now.isBefore(start))) { // NOSONAR
            throw new PreconditionNotMetException("Current time is in recording timeout " + start + " - " + end);
        }
    }

    private void ensureModelIsOnline(Model model) {
        String msg = model.getName() + "'s room is not public";
        try {
            if (!model.isOnline(IGNORE_CACHE)) {
                throw new PreconditionNotMetException(msg);
            }
        } catch (IOException | ExecutionException e) {
            throw new PreconditionNotMetException(msg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PreconditionNotMetException(msg);
        }
    }

    private void ensureRecordUntilIsInFuture(Model model) {
        if (Instant.now().isAfter(model.getRecordUntil())) {
            throw new RecordUntilExpiredException(model.getRecordUntil());
        }
    }

    private void ensureEnoughSpaceForRecording() throws IOException {
        if (recorder.notEnoughSpaceForRecording()) {
            throw new PreconditionNotMetException("Not enough disk space for recording");
        }
    }

    private void ensureDownloadSlotAvailable(Model model) {
        if (!downloadSlotAvailable()) {
            long now = System.currentTimeMillis();
            if ((now - lastPreconditionMessage) > TimeUnit.MINUTES.toMillis(1)) {
                LOG.info("The number of downloads is maxed out");
                lastPreconditionMessage = now;
            }
            // check, if we can stop a recording for a model with lower priority
            Optional<Recording> lowerPrioRecordingProcess = recordingProcessWithLowerPrio(model.getPriority(), model.isForcePriority());
            if (lowerPrioRecordingProcess.isPresent()) {
                RecordingProcess download = lowerPrioRecordingProcess.get().getRecordingProcess();
                Model lowerPrioModel = download.getModel();
                LOG.info("Stopping recording for {}. Prio {} < {}", lowerPrioModel.getName(), lowerPrioModel.getPriority(), model.getPriority());
                recorder.stopRecordingProcess(lowerPrioRecordingProcess.get());
            } else {
                throw new PreconditionNotMetException("Other models have higher prio, not starting recording for " + model.getName());
            }
        }
    }

    private Optional<Recording> recordingProcessWithLowerPrio(int priority, boolean isForced) {
        Recording lowest = null;
        int lowestPrio = Integer.MAX_VALUE;
        for (Recording rec : recorder.getRecordingProcesses()) {
            Model m = rec.getModel();
            if (m.getPriority() < lowestPrio && !m.isForcePriority()) {
                lowest = rec;
                lowestPrio = m.getPriority();
            }
        }
        if (isForced || (lowestPrio < priority)) {
            return Optional.ofNullable(lowest);
        } else {
            return Optional.empty();
        }
    }

    private void ensureModelShouldBeRecorded(Model model) {
        if (!recorder.getModels().contains(model)) {
            throw new PreconditionNotMetException("Model " + model + " has been removed. Restarting of recording cancelled.");
        }
    }

    private void ensureNoRecordingRunningForModel(Model model) {
        if (recorder.isRecordingRunningForModel(model)) {
            throw new PreconditionNotMetException("A recording for model " + model + " is already running");
        }
    }

    private void ensureModelIsNotSuspended(Model model) {
        if (model.isSuspended()) {
            throw new PreconditionNotMetException("Recording for model " + model + " is suspended");
        }
    }

    private void ensureModelIsNotDelayed(Model model) {
        Optional<ModelGroup> modelGroup = recorder.getModelGroup(model);
        if (modelGroup.isPresent() && model.isDelayed()) {
            throw new PreconditionNotMetException("Recording for model " + model + " is delayed");
        }
    }

    private void ensureModelIsNotMarkedForLaterRecording(Model model) {
        if (model.isMarkedForLaterRecording()) {
            throw new PreconditionNotMetException("Model " + model + " is marked for later recording");
        }
    }

    private void ensureRecorderIsActive() {
        if (!recorder.isRunning() || recorder.isShuttingDown()) {
            throw new PreconditionNotMetException("Recorder is not in recording mode");
        }
    }

    private boolean downloadSlotAvailable() {
        int concurrentRecordings = config.getSettings().concurrentRecordings;
        return concurrentRecordings == 0 || concurrentRecordings > 0 && recorder.getRecordingProcesses().size() < concurrentRecordings;
    }

    private void ensureAllModelsFromGroupAreOnlineChecked(Model model) throws IOException, ExecutionException {
        Optional<ModelGroup> modelGroup = recorder.getModelGroup(model);
        if (modelGroup.isEmpty()) {
            return;
        }

        for (String modelUrl : modelGroup.get().getModelUrls()) {
            Optional<Model> otherModel = getModelForUrl(modelUrl);
            if (otherModel.isPresent()) {
                if (otherModel.get().getOnlineState(true) == Model.State.UNCHECKED) {
                    throw new PreconditionNotMetException(otherModel.get() + "'s online state has not been checked yet");
                }
            } else {
                LOG.warn("Model for url {} was not found", modelUrl);
            }
        }
    }

    private void ensureNoOtherFromModelGroupIsRecording(Model model) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        Optional<ModelGroup> modelGroup = recorder.getModelGroup(model);
        if (modelGroup.isEmpty()) {
            return;
        }

        // go through each model in group in descendind priority order, checking this model last amongst same-prio models
        // this is to make sure that we only stop lower priority recordings in favor of this one. I.e. if same-prio model is recordnig, let it run
        for (var groupModel : modelGroup.get().getModelUrls().stream()
                                    .map(modelUrl -> getModelForUrl(modelUrl))
                                    .filter(x -> x.isPresent())
                                    .map(x -> x.get())
                                    .sorted(Comparator
                                                .comparing((Model m) -> m.getPriority()).reversed()   // high to low
                                                .thenComparing((Model m) -> m.getUrl().equals(model.getUrl())))  // this model last (false -> true)
                                    .toList()) {
            if (model.getUrl().equals(groupModel.getUrl())) {
                // no other model with higher prio is online, start recording
                // but before that stop all recordings of models with lower prio
                stopModelsWithLowerPrio(modelGroup.get());
                return;
            } else {
                Model otherModel = groupModel;
                if (!otherModel.isSuspended() && otherModelIsRecorded(otherModel)) {
                    throw new PreconditionNotMetException(otherModel + " from the same group is already recorded");
                } else if (otherModel.getPriority() > model.getPriority() && otherModelCanBeRecorded(otherModel)) {
                    throw new PreconditionNotMetException(otherModel + " from the same group can be recorded");
                }
            }
        }
    }

    private boolean otherModelIsRecorded(Model model) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        return recorder.getCurrentlyRecording().contains(model);
    }

    private void stopModelsWithLowerPrio(ModelGroup modelGroup) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        recorder.getCurrentlyRecording().stream()
                .filter(m -> modelGroup.getModelUrls().contains(m.getUrl()))
                .forEach(m -> recorder.getRecordingProcessForModel(m).ifPresent(recorder::stopRecordingProcess));

    }

    private Optional<Model> getModelForUrl(String modelUrl) {
        return recorder.getModels().stream()
                .filter(m -> Objects.equals(m.getUrl(), modelUrl))
                .findFirst();
    }

    private boolean otherModelCanBeRecorded(Model model) {
        try {
            ensureRecorderIsActive();
            ensureModelIsNotSuspended(model);
            ensureModelIsNotDelayed(model);
            ensureModelIsNotMarkedForLaterRecording(model);
            ensureRecordUntilIsInFuture(model);
            ensureModelShouldBeRecorded(model);
            ensureEnoughSpaceForRecording();
            ensureDownloadSlotAvailable(model);
            ensureModelIsOnline(model);
            return true;
        } catch (PreconditionNotMetException e) {
            // precondition for other model not met
        } catch (IOException e) {
            LOG.warn("Couldn't check if preconditions of other model from group are met. Assuming she's offline", e);
        }
        return false;
    }
}
