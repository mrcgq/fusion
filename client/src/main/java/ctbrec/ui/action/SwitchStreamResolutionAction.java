package ctbrec.ui.action;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import org.eclipse.jetty.io.RuntimeIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.ModelOfflineException;
import ctbrec.ui.StreamSourceSelectionDialog;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class SwitchStreamResolutionAction {
    private static final Logger LOG = LoggerFactory.getLogger(SwitchStreamResolutionAction.class);

    private Node source;
    private Model selectedModel;
    private Recorder recorder;

    public SwitchStreamResolutionAction(Node source, Model selectedModel, Recorder recorder) {
        this.source = source;
        this.selectedModel = selectedModel;
        this.recorder = recorder;
    }

    public CompletableFuture<Void> execute() {
        source.setCursor(Cursor.WAIT);
        var couldntSwitchHeaderText = "Couldn't switch stream resolution";

        return CompletableFuture.supplyAsync(() -> {
            checkOnlineState();
            return selectedModel;
        }, GlobalThreadPool.get())
                .thenAccept(m -> Platform.runLater(() -> {
                    StreamSourceSelectionDialog dialog = new StreamSourceSelectionDialog(source.getScene(), selectedModel);
                    Optional<StreamSource> selectedSource = dialog.showAndWait();
                    if (selectedSource.isPresent()) {
                        StreamSource src = selectedSource.get();
                        if (src != StreamSourceSelectionDialog.LOADING) {
                            int index = dialog.indexOf(selectedSource.get());
                            selectedModel.setStreamUrlIndex(index);
                            try {
                                recorder.switchStreamSource(selectedModel);
                            } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | IOException e) {
                                LOG.error(couldntSwitchHeaderText, e);
                                Dialogs.showError(source.getScene(), "Couldn't switch stream resolution", "Error while switching stream resolution", e);
                            }
                        }
                    }
                    source.setCursor(Cursor.DEFAULT);
                }))
                .exceptionally(ex -> {
                    Dialogs.showError(source.getScene(), couldntSwitchHeaderText, "The resolution can only be changed when the model is online", null);
                    Platform.runLater(() -> source.setCursor(Cursor.DEFAULT));
                    return null;
                });
    }

    private void checkOnlineState() {
        try {
            if (!selectedModel.isOnline(true)) {
                throw new ModelOfflineException(selectedModel);
            }
        } catch (IOException | ExecutionException e) {
            throw new RuntimeIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e); // NOSONAR
        }
    }
}
