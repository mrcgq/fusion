package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.ResumePriorityTask;
import javafx.scene.Node;

public class ResumePriorityAction extends AbstractModelAction {

    public ResumePriorityAction(Node source, List<Model> models, Recorder recorder) {
        super(source, models, recorder, new ResumePriorityTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't resume respecting priority", "Resuming priority of {0} failed:");
    }
}
