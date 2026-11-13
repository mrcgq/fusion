package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.PauseRecordingTask;
import javafx.scene.Node;

public class PauseAction extends AbstractModelAction {

    public PauseAction(Node source, List<Model> models, Recorder recorder) {
        super(source, models, recorder, new PauseRecordingTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't pause recording", "Pausing recording of {0} failed:");
    }
}
