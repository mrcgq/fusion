package ctbrec.ui.action;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.controls.Dialogs;
import javafx.scene.Cursor;
import javafx.scene.Node;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
public class OpenRecordingsDir {

    private final Node source;
    private final Model selectedModel;

    public void execute() {
        source.setCursor(Cursor.WAIT);
        var dir = Config.getInstance().getFileForRecording(selectedModel, ".mp4", Instant.now());
        dir = findDeepestExistingDir(dir);
        log.info("Directory for model is {}", dir.getAbsolutePath());
        if (dir.exists()) {
            final File directory = dir;
            GlobalThreadPool.submit(() -> DesktopIntegration.open(directory));
        } else {
            Dialogs.showError(source.getScene(), "Directory does not exist", "There are no recordings for this model", null);
        }
        source.setCursor(Cursor.DEFAULT);
    }

    private File findDeepestExistingDir(File dir) {
        while (!dir.exists()) {
            if (dir.getParentFile() == null) {
                return dir;
            } else {
                dir = dir.getParentFile();
                if (dir.exists()) {
                    break;
                }
            }
        }
        return dir;
    }
}
