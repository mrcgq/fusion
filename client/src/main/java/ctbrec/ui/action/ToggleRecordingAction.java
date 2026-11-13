package ctbrec.ui.action;

import ctbrec.GlobalThreadPool;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.ToggleButton;

public class ToggleRecordingAction {

    private ToggleButton toggleButton;
    private Recorder recorder;
    private boolean pause;

    public ToggleRecordingAction(ToggleButton toggleButton, Recorder recorder) {
        this.toggleButton = toggleButton;
        this.recorder = recorder;
        pause = toggleButton.isSelected();
    }

    public void execute() {
        toggleButton.setCursor(Cursor.WAIT);
        GlobalThreadPool.submit(() -> {
            try {
                if (pause) {
                    recorder.pause();
                    Platform.runLater(() -> toggleButton.setText("Resume Recorder"));
                } else {
                    recorder.resume();
                    Platform.runLater(() -> toggleButton.setText("Pause Recorder"));
                }
            } catch (Exception e) {
                Dialogs.showError(toggleButton.getScene(), "Toggle Recorder", "An error ocurred while toggling the recorder", e);
                Platform.runLater(() -> toggleButton.setSelected(!toggleButton.isSelected()));
            } finally {
                Platform.runLater(() -> toggleButton.setCursor(Cursor.DEFAULT));
            }
        });
    }
}
