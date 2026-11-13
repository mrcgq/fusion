package ctbrec.ui.action;

import java.util.List;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.scene.Node;

public class MarkForLaterRecordingAction extends ModelMassEditAction {

    public MarkForLaterRecordingAction(Node source, List<? extends Model> models, boolean recordLater, Recorder recorder) {
        super(source, models);
        action = m -> {
            try {
                recorder.markForLaterRecording(m, recordLater);
            } catch (Exception e) {
                Platform.runLater(() -> Dialogs.showError(source.getScene(), "Couldn't model mark model for later recording",
                        "Marking for later recording of " + m.getName() + " failed", e));
            }
        };
    }
}
