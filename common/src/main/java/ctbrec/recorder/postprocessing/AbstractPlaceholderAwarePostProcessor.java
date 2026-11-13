package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.notes.ModelNotesService;
import ctbrec.variableexpansion.ConfigVariableExpander;
import ctbrec.variableexpansion.ModelVariableExpander;
import ctbrec.variableexpansion.RecordingVariableExpander;
import ctbrec.variableexpansion.functions.AntlrSyntacErrorAdapter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class AbstractPlaceholderAwarePostProcessor extends AbstractPostProcessor {

    public String fillInPlaceHolders(String input, PostProcessingContext ctx) {
        return this.fillInPlaceHolders(input, ctx, null);
    }

    public String fillInPlaceHolders(String input, PostProcessingContext ctx, AntlrSyntacErrorAdapter errorListener) {
        Recording rec = ctx.getRecording();
        Config config = ctx.getConfig();
        ModelNotesService modelNotesService = ctx.getModelNotesService();

        ModelVariableExpander modelExpander = new ModelVariableExpander(rec.getModel(), modelNotesService, ctx.getRecorder(), errorListener);
        RecordingVariableExpander recordingExpander = new RecordingVariableExpander(rec, errorListener);
        ConfigVariableExpander configExpander = new ConfigVariableExpander(config, errorListener);
        modelExpander.getPlaceholderValueSuppliers().putAll(recordingExpander.getPlaceholderValueSuppliers());
        modelExpander.getPlaceholderValueSuppliers().putAll(configExpander.getPlaceholderValueSuppliers());
        return modelExpander.expand(input);
    }
}
