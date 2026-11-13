package ctbrec.ui.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.download.StreamSource;
import ctbrec.ui.RecordUntilDialog;
import ctbrec.ui.StreamSourceSelectionDialog;
import ctbrec.ui.tasks.StartRecordingTask;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class StartRecordingAction extends AbstractModelAction {

    private static final String ERROR_HEADER = "Couldn't start recording";
    private static final String ERROR_MSG = "Starting recording of {0} failed:";

    private boolean showRecordUntilDialog = false;

    public StartRecordingAction(Node source, List<Model> models, Recorder recorder) {
        super(source, models, recorder, new StartRecordingTask(recorder));
    }

    public StartRecordingAction showRecordUntilDialog() {
        showRecordUntilDialog = true;
        return this;
    }

    public CompletableFuture<List<Result>> execute() {
        boolean selectSource = Config.getInstance().getSettings().chooseStreamQuality;
        if (selectSource || showRecordUntilDialog) {
            var future = executeSync();
            Platform.runLater(() -> source.setCursor(Cursor.DEFAULT));
            return future;
        } else {
            return super.execute(ERROR_HEADER, ERROR_MSG);
        }
    }

    private CompletableFuture<List<Result>> executeSync() {
        var result = new ArrayList<Result>(models.size());
        for (final Model model : models) {
            if (showRecordUntilDialog) {
                RecordUntilDialog dialog = new RecordUntilDialog(source, model);
                var confirmed = dialog.showAndWait();
                if (!confirmed) {
                    continue;
                }
            }

            boolean selectSource = Config.getInstance().getSettings().chooseStreamQuality;
            if (selectSource) {
                var dialog = new StreamSourceSelectionDialog(source.getScene(), model);
                Optional<StreamSource> selection = dialog.showAndWait();
                if (selection.isPresent()) {
                    StreamSource src = selection.get();
                    if (src != StreamSourceSelectionDialog.LOADING) {
                        int index = dialog.indexOf(src);
                        model.setStreamUrlIndex(index);
                    }
                } else {
                    continue;
                }
            }

            if (showRecordUntilDialog) {
                model.setSuspended(false);
            }
            new StartRecordingTask(recorder).executeSync(model)
            .whenComplete((mdl, ex) -> result.add(new Result(mdl, ex)));
        }
        checkResultForErrors(ERROR_HEADER, ERROR_MSG, result);
        return CompletableFuture.completedFuture(result);
    }
}
