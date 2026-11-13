package ctbrec.ui.action;

import java.util.List;
import java.util.function.Consumer;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.JavaFxModel;
import ctbrec.ui.action.AbstractModelAction.Result;
import ctbrec.ui.controls.Dialogs;
import javafx.scene.Node;

public class IgnoreModelsAction {
    private Node source;
    private List<? extends Model> selectedModels;
    private Recorder recorder;
    private boolean withRemoveDialog;

    public IgnoreModelsAction(Node source, List<? extends Model> selectedModels, Recorder recorder, boolean withRemoveDialog) {
        this.source = source;
        this.selectedModels = selectedModels;
        this.recorder = recorder;
        this.withRemoveDialog = withRemoveDialog;
    }

    public void execute() {
        execute(model -> {});
    }

    public void execute(Consumer<Model> callback) {
        var settings = Config.getInstance().getSettings();
        var confirmed = true;
        if (settings.confirmationForDangerousActions) {
            int n = selectedModels.size();
            String plural = n > 1 ? "s" : "";
            String header = "This will add " + n + " model" + plural + " to the ignore list";
            confirmed = Dialogs.showConfirmDialog("Ignore Models", "Continue?", header, source.getScene());
        }
        if (confirmed) {
            for (Model model : selectedModels) {
                var modelToIgnore = unwrap(model);
                settings.ignoredModels.add(modelToIgnore.getUrl());
            }
            if (withRemoveDialog) {
                boolean removeAsWell = Dialogs.showConfirmDialog("Ignore Model", null, "Remove as well?", source.getScene());
                if (removeAsWell) {
                    new StopRecordingAction(source, selectedModels, recorder).execute()
                    .whenComplete((r, ex) -> r.stream().map(Result::getModel).forEach(callback::accept));
                }
            } else {
                for (Model model : selectedModels) {
                    callback.accept(model);
                }
            }
        }
    }

    private Model unwrap(Model model) {
        if (model instanceof JavaFxModel) {
            return ((JavaFxModel) model).getDelegate();
        } else {
            return model;
        }
    }
}
