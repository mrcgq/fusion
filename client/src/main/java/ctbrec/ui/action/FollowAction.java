package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.FollowTask;
import javafx.scene.Node;

public class FollowAction extends AbstractModelAction {

    public FollowAction(Node source, List<? extends Model> models, Recorder recorder) {
        super(source, models, recorder, new FollowTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't follow model", "Following of {0} failed:");
    }
}
