package ctbrec.recorder;

import ctbrec.*;
import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.recorder.download.RecordingProcess;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.HOURS;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordingPreconditionsTest {

    private Config config;
    private final Settings settings = new Settings();

    @BeforeEach
    void setup() {
        config = mock(Config.class);
        when(config.getSettings()).thenReturn(settings);
        try (MockedStatic<Config> staticConfigMock = mockStatic(Config.class)) {
            staticConfigMock.when(Config::getInstance).thenReturn(config);
        }
    }

    @Test
    void testRecorderNotInRecordingMode() {
        var recorder = mock(SimplifiedLocalRecorder.class);
        Model model = mock(Model.class);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertEquals("Recorder is not in recording mode", ex.getMessage());
    }

    @Test
    void testModelIsSuspended() {
        var recorder = mock(SimplifiedLocalRecorder.class);
        Model model = mock(Model.class);

        when(recorder.isRunning()).thenReturn(true);
        when(model.isSuspended()).thenReturn(true);
        when(model.toString()).thenReturn("Mockita Boobilicious");

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertEquals("Recording for model Mockita Boobilicious is suspended", ex.getMessage());
    }

    @Test
    void testModelMarkedForLaterRecording() {
        var recorder = mock(SimplifiedLocalRecorder.class);
        Model model = mock(Model.class);

        when(recorder.isRunning()).thenReturn(true);
        when(model.isMarkedForLaterRecording()).thenReturn(true);
        when(model.toString()).thenReturn("Mockita Boobilicious");

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertEquals("Model Mockita Boobilicious is marked for later recording", ex.getMessage());
    }

    @Test
    void testRecordUntil() {
        var recorder = mock(SimplifiedLocalRecorder.class);
        Model model = mock(Model.class);

        when(recorder.isRunning()).thenReturn(true);
        when(model.getRecordUntil()).thenReturn(Instant.now().minus(1, HOURS));
        when(model.toString()).thenReturn("Mockita Boobilicious");

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertTrue(ex.getMessage().contains("Recording expired at "));
    }

    @Test
    void testRecordingAlreadyRunning() throws IOException, ExecutionException, InterruptedException {
        var recorder = mock(SimplifiedLocalRecorder.class);
        Model model = mock(Model.class);
        when(model.isOnline(true)).thenReturn(true);

        when(recorder.isRunning()).thenReturn(true);
        Recording rec = new Recording();
        rec.setModel(model);
        when(recorder.getRecordingProcesses()).thenReturn(List.of(rec));
        when(model.getRecordUntil()).thenReturn(Instant.MAX);
        when(model.toString()).thenReturn("Mockita Boobilicious");
        when(recorder.getModels()).thenReturn(List.of(model));
        when(recorder.isRecordingRunningForModel(model)).thenReturn(true);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertEquals("A recording for model Mockita Boobilicious is already running", ex.getMessage());
    }

    @Test
    void testModelShouldBeRecorded() throws IOException, ExecutionException, InterruptedException {
        var recorder = mock(SimplifiedLocalRecorder.class);
        Model model = mock(Model.class);

        when(recorder.isRunning()).thenReturn(true);
        List<Model> modelsToRecord = new LinkedList<>();
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(model.getRecordUntil()).thenReturn(Instant.MAX);
        when(model.toString()).thenReturn("Mockita Boobilicious");
        when(model.isOnline(true)).thenReturn(true);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertEquals("Model Mockita Boobilicious has been removed. Restarting of recording cancelled.", ex.getMessage());

        modelsToRecord.add(model);
        reset(recorder);
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);
        assertDoesNotThrow(() -> preconditions.check(model));
    }

    @Test
    void testEnoughSpace() throws IOException, ExecutionException, InterruptedException {
        Model model = mock(Model.class);
        when(model.getRecordUntil()).thenReturn(Instant.MAX);
        when(model.toString()).thenReturn("Mockita Boobilicious");
        when(model.isOnline(true)).thenReturn(true);

        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        modelsToRecord.add(model);
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(true);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(model));
        assertEquals("Not enough disk space for recording", ex.getMessage());
    }

    @Test
    void testNoOtherFromGroupIsRecording() throws IOException, ExecutionException, InterruptedException {
        Model mockita = mock(Model.class);
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.toString()).thenReturn("Mockita Boobilicious");
        when(mockita.isOnline(true)).thenReturn(true);
        when(mockita.getUrl()).thenReturn("http://localhost/mockita");
        when(mockita.getPriority()).thenReturn(0);

        Model theOtherOne = mock(Model.class);
        when(theOtherOne.getRecordUntil()).thenReturn(Instant.MAX);
        when(theOtherOne.toString()).thenReturn("The Other One");
        when(theOtherOne.isOnline(true)).thenReturn(true);
        when(theOtherOne.getUrl()).thenReturn("http://localhost/theOtherOne");
        when(theOtherOne.getPriority()).thenReturn(10);

        ModelGroup group = new ModelGroup();
        group.add(theOtherOne);
        group.add(mockita);

        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        modelsToRecord.add(theOtherOne);
        modelsToRecord.add(mockita);
        settings.models = modelsToRecord.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);
        when(recorder.getModelGroup(theOtherOne)).thenReturn(Optional.of(group));
        when(recorder.getModelGroup(mockita)).thenReturn(Optional.of(group));
        Recording recording = new Recording();
        recording.setModel(theOtherOne);
        when(recorder.getRecordingProcesses()).thenReturn(List.of(recording));


        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertEquals("The Other One from the same group can be recorded", ex.getMessage());
    }

    @Test
    void testModelIsOffline() throws IOException, ExecutionException, InterruptedException {
        Model mockita = mock(Model.class);
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.getName()).thenReturn("Mockita Boobilicious");
        when(mockita.toString()).thenReturn("Mockita Boobilicious");
        when(mockita.isOnline(true)).thenReturn(false);

        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        modelsToRecord.add(mockita);
        settings.models = modelsToRecord.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertEquals("Mockita Boobilicious's room is not public", ex.getMessage());
    }

    @Test
    void testModelIsOnlineWithExpection() throws IOException, ExecutionException, InterruptedException {
        Model mockita = mock(Model.class);
        when(mockita.isOnline(true)).thenThrow(new IOException("Service unavailable"));
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.getName()).thenReturn("Mockita Boobilicious");

        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        modelsToRecord.add(mockita);
        settings.models = modelsToRecord.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertEquals("Mockita Boobilicious's room is not public", ex.getMessage());

        reset(mockita);
        when(mockita.isOnline(true)).thenThrow(new InterruptedException());
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.getName()).thenReturn("Mockita Boobilicious");
        ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertEquals("Mockita Boobilicious's room is not public", ex.getMessage());
    }

    @Test
    void testDownloadSlotsExhausted() throws IOException, ExecutionException, InterruptedException {
        settings.concurrentRecordings = 1;

        Model mockita = mock(Model.class);
        when(mockita.isOnline(true)).thenReturn(true);
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.getName()).thenReturn("Mockita Boobilicious");

        Model theOtherOne = mock(Model.class);
        when(theOtherOne.getRecordUntil()).thenReturn(Instant.MAX);
        when(theOtherOne.toString()).thenReturn("The Other One");
        when(theOtherOne.isOnline(true)).thenReturn(true);
        when(theOtherOne.getUrl()).thenReturn("http://localhost/theOtherOne");

        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        modelsToRecord.add(mockita);
        settings.models = modelsToRecord.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);
        List<Recording> recordingProcesses = new ArrayList<>();
        Recording rec = new Recording();
        rec.setModel(theOtherOne);
        recordingProcesses.add(rec);
        when(recorder.getRecordingProcesses()).thenReturn(recordingProcesses);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertEquals("Other models have higher prio, not starting recording for Mockita Boobilicious", ex.getMessage());

        settings.concurrentRecordings = -1;
        ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertEquals("Other models have higher prio, not starting recording for Mockita Boobilicious", ex.getMessage());

        settings.concurrentRecordings = 0;
        assertDoesNotThrow(() -> preconditions.check(mockita));

        settings.concurrentRecordings = 1;
        recordingProcesses.clear();
        assertDoesNotThrow(() -> preconditions.check(mockita));
    }

    @Test
    void testDownloadSlotFreedUp() throws IOException, ExecutionException, InterruptedException {
        settings.concurrentRecordings = 1;

        Model mockita = mock(Model.class);
        when(mockita.isOnline(true)).thenReturn(true);
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.getName()).thenReturn("Mockita Boobilicious");
        when(mockita.getPriority()).thenReturn(100);

        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        modelsToRecord.add(mockita);
        settings.models = modelsToRecord.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);
        List<Recording> recordingProcesses = new ArrayList<>();
        when(recorder.getRecordingProcesses()).thenReturn(recordingProcesses);

        Model theOtherOne = mock(Model.class);
        when(theOtherOne.getRecordUntil()).thenReturn(Instant.MAX);
        when(theOtherOne.toString()).thenReturn("The Other One");
        when(theOtherOne.isOnline(true)).thenReturn(true);
        when(theOtherOne.getUrl()).thenReturn("http://localhost/theOtherOne");
        when(theOtherOne.getPriority()).thenReturn(50);
        recordingProcesses.add(mockRecordingProcess(theOtherOne));

        Model lowestPrio = mock(Model.class);
        when(lowestPrio.getRecordUntil()).thenReturn(Instant.MAX);
        when(lowestPrio.toString()).thenReturn("Lowest");
        when(lowestPrio.isOnline(true)).thenReturn(true);
        when(lowestPrio.getUrl()).thenReturn("http://localhost/lowest");
        when(lowestPrio.getPriority()).thenReturn(1);
        Recording lowestPrioRecording = new Recording();
        RecordingProcess recordingProcess = mock(RecordingProcess.class);
        when(recordingProcess.getModel()).thenReturn(lowestPrio);
        lowestPrioRecording.setRecordingProcess(recordingProcess);
        lowestPrioRecording.setModel(lowestPrio);
        recordingProcesses.add(lowestPrioRecording);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        assertDoesNotThrow(() -> preconditions.check(mockita));
        verify(recorder).stopRecordingProcess(lowestPrioRecording);
    }

    @Test
    void testNotInTimeoutPeriod() throws IOException, ExecutionException, InterruptedException {
        Model mockita = mock(Model.class);
        when(mockita.isOnline(true)).thenReturn(true);
        when(mockita.getRecordUntil()).thenReturn(Instant.MAX);
        when(mockita.getName()).thenReturn("Mockita Boobilicious");
        when(mockita.getPriority()).thenReturn(100);
        var recorder = mock(SimplifiedLocalRecorder.class);
        List<Model> modelsToRecord = new LinkedList<>();
        settings.timeoutRecordingStartingAt = LocalTime.now().minusHours(1).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        modelsToRecord.add(mockita);
        settings.models = modelsToRecord.stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList());
        when(recorder.isRunning()).thenReturn(true);
        when(recorder.getModels()).thenReturn(modelsToRecord);
        when(recorder.notEnoughSpaceForRecording()).thenReturn(false);
        List<Recording> recordingProcesses = new ArrayList<>();
        when(recorder.getRecordingProcesses()).thenReturn(recordingProcesses);

        RecordingPreconditions preconditions = new RecordingPreconditions(recorder, config);
        PreconditionNotMetException ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertTrue(ex.getMessage().startsWith("Current time is in recording timeout"));

        settings.timeoutRecordingStartingAt = LocalTime.now().minusHours(1).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().minusHours(2).truncatedTo(ChronoUnit.MINUTES);
        ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertTrue(ex.getMessage().startsWith("Current time is in recording timeout"));

        settings.timeoutRecordingStartingAt = LocalTime.now().minusHours(1).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().minusHours(2).truncatedTo(ChronoUnit.MINUTES);
        ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertTrue(ex.getMessage().startsWith("Current time is in recording timeout"));

        settings.timeoutRecordingStartingAt = LocalTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        ex = assertThrows(PreconditionNotMetException.class, () -> preconditions.check(mockita));
        assertTrue(ex.getMessage().startsWith("Current time is in recording timeout"));

        settings.timeoutRecordingStartingAt = LocalTime.of(0, 0);
        settings.timeoutRecordingEndingAt = LocalTime.of(0, 0);
        assertDoesNotThrow(() -> preconditions.check(mockita));

        settings.timeoutRecordingStartingAt = LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().plusHours(2).truncatedTo(ChronoUnit.MINUTES);
        assertDoesNotThrow(() -> preconditions.check(mockita));
        settings.timeoutRecordingStartingAt = LocalTime.now().minusHours(2).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().minusHours(1).truncatedTo(ChronoUnit.MINUTES);
        assertDoesNotThrow(() -> preconditions.check(mockita));

        settings.timeoutRecordingStartingAt = LocalTime.now().plusHours(1).truncatedTo(ChronoUnit.MINUTES);
        settings.timeoutRecordingEndingAt = LocalTime.now().minusHours(1).truncatedTo(ChronoUnit.MINUTES);
        assertDoesNotThrow(() -> preconditions.check(mockita));
    }

    private Recording mockRecordingProcess(Model model) {
        RecordingProcess download = mock(RecordingProcess.class);
        when(download.getModel()).thenReturn(model);
        Recording runningRecording = mock(Recording.class);
        when(runningRecording.getRecordingProcess()).thenReturn(download);
        when(runningRecording.getModel()).thenReturn(model);
        return runningRecording;
    }
}
