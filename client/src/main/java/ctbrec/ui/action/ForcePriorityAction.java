package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.ForcePriorityTask;
import javafx.scene.Node;

public class ForcePriorityAction extends AbstractModelAction {

    public ForcePriorityAction(Node source, List<Model> models, Recorder recorder) {
        super(source, models, recorder, new ForcePriorityTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't force ignoring priority", "Force priority of {0} failed:");
    }
}