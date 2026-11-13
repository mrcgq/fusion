package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.UnfollowTask;
import javafx.scene.Node;

public class UnfollowAction extends AbstractModelAction {

    public UnfollowAction(Node source, List<? extends Model> models, Recorder recorder) {
        super(source, models, recorder, new UnfollowTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't unfollow model", "Unfollowing of {0} failed:");
    }
}
