package ctbrec.ui.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.action.ForcePriorityAction;
import ctbrec.ui.action.ResumePriorityAction;
import javafx.scene.Node;

public class ForcePriorityHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ForcePriorityHandler.class);

    private Node source;
    private Recorder recorder;
    private Runnable callback;

    public ForcePriorityHandler(Node source, Recorder recorder, Runnable callback) {
        this.source = source;
        this.recorder = recorder;
        this.callback = callback;
    }

    protected void forcePriority(List<Model> selectedModels) {
        new ForcePriorityAction(source, selectedModels, recorder).execute()
                .exceptionally(ex -> {
                    LOG.error("Error while forcing ignore priority", ex);
                    return null;
                }).whenComplete((r, ex) -> executeCallback());
    }

    protected void resumePriority(List<Model> selectedModels) {
        new ResumePriorityAction(source, selectedModels, recorder).execute()
                .exceptionally(ex -> {
                    LOG.error("Error while resuming respecting priority", ex);
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
