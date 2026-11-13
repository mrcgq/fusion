package ctbrec.ui.tabs.recorded;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.recorder.Recorder;

import java.util.Optional;

public class ModelName {
    private final Model mdl;
    private final Recorder rec;

    public ModelName(Model model, Recorder recorder) {
        mdl = model;
        rec = recorder;
    }

    @Override
    public String toString() {
        Optional<ModelGroup> modelGroup = rec.getModelGroup(mdl);
        String s;
        if (modelGroup.isPresent()) {
            s = modelGroup.get().getName() + " (aka " + mdl.getDisplayName() + ')';
        } else {
            return mdl.getDisplayName();
        }
        return s;
    }
}
