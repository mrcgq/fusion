package ctbrec.ui.action;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ctbrec.Model;
import ctbrec.ModelGroup;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.scene.Node;

public class PauseGroupAction extends ModelMassEditAction {

    private Recorder recorder;
    private Model model;

    public PauseGroupAction(Node source, Recorder recorder, Model model) {
        super.source = source;
        this.recorder = recorder;
        this.model = model;

        action = m -> {
            try {
                recorder.suspendRecording(m);
            } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                Platform.runLater(() -> Dialogs.showError(source.getScene(), "Couldn't pause model", "Pausing recording of " + m.getName() + " failed", e));
            }
        };
    }

    @Override
    protected List<Model> getModels() {
        Optional<ModelGroup> optionalGroup = recorder.getModelGroup(model);
        if (optionalGroup.isPresent()) {
            ModelGroup group = optionalGroup.get();
            return recorder.getModels().stream() //
                    .filter(m -> group.getModelUrls().contains(m.getUrl())) //
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }
    }
}
