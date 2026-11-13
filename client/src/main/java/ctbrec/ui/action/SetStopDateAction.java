package ctbrec.ui.action;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.RecordUntilDialog;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tasks.StartRecordingTask;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class SetStopDateAction {

    private static final Logger LOG = LoggerFactory.getLogger(SetStopDateAction.class);

    private Node source;
    private Model model;
    private Recorder recorder;

    public SetStopDateAction(Node source, Model model, Recorder recorder) {
        this.source = source;
        this.model = model;
        this.recorder = recorder;
    }

    public CompletableFuture<Boolean> execute() {
        source.setCursor(Cursor.WAIT);
        RecordUntilDialog dialog = new RecordUntilDialog(source, model);
        boolean userClickedOk = dialog.showAndWait();
        return createAsyncTask(userClickedOk);
    }

    private CompletableFuture<Boolean> createAsyncTask(boolean userClickedOk) {
        return CompletableFuture.supplyAsync(() -> {
            if (userClickedOk) {
                setRecordingTimeLimit();
            }
            return true;
        }, GlobalThreadPool.get()).whenComplete((r, e) -> {
            Platform.runLater(() -> source.setCursor(Cursor.DEFAULT));
            if (e != null) {
                LOG.error("Error", e);
            }
        });
    }

    private void setRecordingTimeLimit() {
        try {
            if (!recorder.isTracked(model) || model.isMarkedForLaterRecording()) {
                new StartRecordingTask(recorder).executeSync(model)
                .thenAccept(m -> {
                    try {
                        recorder.stopRecordingAt(m);
                    } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e1) {
                        showError(e1);
                    }
                }).exceptionally(ex -> {
                    showError(ex);
                    return null;
                });
            } else {
                recorder.stopRecordingAt(model);
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            showError(e);
        }
    }

    private void showError(Throwable t) {
        Platform.runLater(() -> Dialogs.showError(source.getScene(), "Error", "Couln't set stop date", t));
    }


}
