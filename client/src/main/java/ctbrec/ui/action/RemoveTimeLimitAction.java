package ctbrec.ui.action;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class RemoveTimeLimitAction {

    private Model selectedModel;
    private Node source;
    private Recorder recorder;

    public RemoveTimeLimitAction(Node source, Model selectedModel, Recorder recorder) {
        this.source = source;
        this.selectedModel = selectedModel;
        this.recorder = recorder;
    }

    public CompletableFuture<Boolean> execute() {
        source.setCursor(Cursor.WAIT);
        var unlimited = Instant.ofEpochMilli(Model.RECORD_INDEFINITELY);
        return CompletableFuture.supplyAsync(() -> {
            try {
                selectedModel.setRecordUntil(unlimited);
                recorder.stopRecordingAt(selectedModel);
                return true;
            } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                Dialogs.showError(source.getScene(), "Error", "Couln't remove stop date", e);
                return false;
            }
        }, GlobalThreadPool.get()).whenComplete((r,e) -> source.setCursor(Cursor.DEFAULT));
    }
}
