package ctbrec.ui.action;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.scene.control.Button;

public class CheckModelAccountAction {
    private static final Logger LOG = LoggerFactory.getLogger(CheckModelAccountAction.class);

    private Button b;

    private Recorder recorder;

    private String buttonText;

    public CheckModelAccountAction(Button b, Recorder recorder) {
        this.b = b;
        this.recorder = recorder;
        buttonText = b.getText();
    }

    public void execute(Predicate<Model> filter) {
        b.setDisable(true);
        Runnable checker = (() -> {
            List<Model> deletedAccounts = new ArrayList<>();
            try {
                checkModelAccounts(filter, deletedAccounts);
            } finally {
                showResult(deletedAccounts);
            }
        });
        GlobalThreadPool.submit(checker);
    }

    private void showResult(List<Model> deletedAccounts) {
        Platform.runLater(() -> {
            b.setDisable(false);
            b.setText(buttonText);
            if (!deletedAccounts.isEmpty()) {
                var sb = new StringBuilder();
                for (Model deletedModel : deletedAccounts) {
                    String name = deletedModel.getDisplayName() + " ".repeat(30);
                    name = name.substring(0, 30);
                    sb.append(name).append(' ').append('(').append(deletedModel.getUrl()).append(')').append('\n');
                }
                boolean remove = Dialogs.showConfirmDialog("Deleted Accounts", sb.toString(),
                        "The following accounts seem to have been deleted. Do you want to remove them?", b.getScene());
                if (remove) {
                    new StopRecordingAction(b, deletedAccounts, recorder).execute();
                }
            }
        });
    }

    private void checkModelAccounts(Predicate<Model> filter, List<Model> deletedAccounts) {
        List<Model> models = recorder.getModels().stream() //
                .filter(filter) //
                .collect(Collectors.toList());
        int total = models.size();
        for (var i = 0; i < total; i++) {
            final int counter = i+1;
            Platform.runLater(() -> b.setText(buttonText + ' ' + counter + '/' + total));
            var modelToCheck = models.get(i);
            try {
                if (!modelToCheck.exists()) {
                    deletedAccounts.add(modelToCheck);
                }
            } catch (IOException e) {
                LOG.warn("Couldn't check, if model account still exists", e);
            }
        }
    }
}
