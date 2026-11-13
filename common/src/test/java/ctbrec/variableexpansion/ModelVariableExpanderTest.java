package ctbrec.variableexpansion;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Settings;
import ctbrec.UnknownModel;
import ctbrec.notes.ModelNotesService;
import ctbrec.recorder.Recorder;
import ctbrec.sites.chaturbate.Chaturbate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ModelVariableExpanderTest {

    Model model;
    Config config;
    MockedStatic<Config> configStatic;

    ModelNotesService modelNotesService;

    @BeforeEach
    void setup() throws IOException {
        UnknownModel unknownModel = new UnknownModel();
        Chaturbate chaturbate = new Chaturbate();
        unknownModel.setName("Pussy_Galore");
        unknownModel.setDisplayName("Pussy Galore");
        unknownModel.setSite(chaturbate);
        this.model = unknownModel;
        this.config = mockConfig();
        this.modelNotesService = mock(ModelNotesService.class);
        when(modelNotesService.loadModelNotes(any())).thenReturn(Optional.of("tag, foo, bar"));
    }

    @AfterEach
    public void teardown() {
        if (configStatic != null) {
            configStatic.close();
            configStatic = null;
        }
    }

    @Test
    void testMultipleVariablesAsParameter() {
        Recorder recorder = mock(Recorder.class);
        ModelVariableExpander modelVariableExpander = new ModelVariableExpander(model, modelNotesService, recorder, null);
        assertEquals("pussy_galore asdf pussy_galore", modelVariableExpander.expand("$lower(${modelName} ASDF ${modelName})"));
    }

    Config mockConfig() {
        Config config = mock(Config.class);
        Settings settings = mockSettings();
        when(config.getSettings()).thenReturn(settings);
        configStatic = mockStatic(Config.class);
        configStatic.when(Config::getInstance).thenReturn(config);
        return config;
    }

    Settings mockSettings() {
        Settings settings = new Settings();
        settings.recordingsDir = "/tmp/rec";
        return settings;
    }
}
