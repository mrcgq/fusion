package ctbrec.ui.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.action.PauseAction;
import ctbrec.ui.action.ResumeAction;
import javafx.scene.Node;

public class PauseResumeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(PauseResumeHandler.class);

    private Node source;
    private Recorder recorder;
    private Runnable callback;

    public PauseResumeHandler(Node source, Recorder recorder, Runnable callback) {
        this.source = source;
        this.recorder = recorder;
        this.callback = callback;
    }

    protected void pause(List<Model> selectedModels) {
        new PauseAction(source, selectedModels, recorder).execute()
        .exceptionally(ex -> {
            LOG.error("Error while pausing recordings", ex);
            return null;
        }).whenComplete((r, ex) -> executeCallback());
    }

    protected void resume(List<Model> selectedModels) {
        new ResumeAction(source, selectedModels, recorder).execute()
        .exceptionally(ex -> {
            LOG.error("Error while resuming recordings", ex);
            return null;
        }).whenComplete((r, ex) -> executeCallback());
    }

    private void executeCallback() {
        try {
            callback.run();
        } catch (Exception e) {
            LOG.error("Error while executing menu callback", e);
        }
    }
}
