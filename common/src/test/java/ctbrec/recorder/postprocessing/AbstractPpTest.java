package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.Settings;
import ctbrec.notes.ModelNotesService;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.RecordingManager;
import ctbrec.sites.Site;
import ctbrec.sites.stripchat.Stripchat;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.MockedStatic;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public abstract class AbstractPpTest {
    Path baseDir;
    Path recDir;
    File original;
    File postProcessed;
    File originalDir;
    File postProcessedDir;
    Instant now = Instant.now();
    RecordingManager recordingManager;

    MockedStatic<Config> configStatic;
    ModelNotesService modelNotesService;

    @BeforeEach
    public void setup() throws IOException {
        baseDir = Files.createTempDirectory("ctbrec_test_");
        recDir = baseDir.resolve("recordings");
        original = new File(recDir.toFile(), "original.ts");
        postProcessed = new File(recDir.toFile(), "postProcessed.ts");
        originalDir = new File(recDir.toFile(), "original");
        postProcessedDir = new File(recDir.toFile(), "postProcessed");
        Files.createDirectories(original.getParentFile().toPath());
        Files.writeString(original.toPath(), "foobar", CREATE_NEW, WRITE, TRUNCATE_EXISTING);
        Files.writeString(postProcessed.toPath(), "foobar", CREATE_NEW, WRITE, TRUNCATE_EXISTING);
        Files.createDirectories(originalDir.toPath());
        FileUtils.touch(new File(originalDir, "playlist.m3u8"));
        modelNotesService = mock(ModelNotesService.class);
        when(modelNotesService.loadModelNotes(any())).thenReturn(Optional.of("tag, foo, bar"));
    }

    @AfterEach
    public void teardown() throws IOException {
        FileUtils.deleteDirectory(baseDir.toFile());
        if (configStatic != null) {
            configStatic.close();
            configStatic = null;
        }
    }

    Config mockConfig() {
        Config config = mock(Config.class);
        Settings settings = mockSettings();
        when(config.getSettings()).thenReturn(settings);
        when(config.getConfigDir()).thenReturn(new File(baseDir.toFile(), "config"));
        configStatic = mockStatic(Config.class);
        configStatic.when(Config::getInstance).thenReturn(config);
        return config;
    }

    Model mockModel() {
        Site site = new Stripchat();
        Model model = site.createModel("Mockita_Boobilicious");
        model.setDisplayName("Mockita Boobilicious");
        return model;
    }

    Settings mockSettings() {
        Settings settings = new Settings();
        settings.recordingsDir = recDir.toString();
        return settings;
    }

    Recorder mockRecorder() {
        Recorder recorder = mock(Recorder.class);
        return recorder;
    }

    PostProcessingContext createPostProcessingContext(Recording rec, RecordingManager recordingManager, Config config) {
        return createPostProcessingContext(rec, recordingManager, config, null);
    }

    PostProcessingContext createPostProcessingContext(Recording rec, RecordingManager recordingManager, Config config, ModelNotesService modelNotesService) {
        PostProcessingContext ctx = new PostProcessingContext();
        ctx.setConfig(config);
        ctx.setRecording(rec);
        ctx.setRecorder(mockRecorder());
        ctx.setRecordingManager(recordingManager);
        ctx.setModelNotesService(modelNotesService);
        return ctx;
    }
}
