package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.StopRecordingTask;
import javafx.scene.Node;

public class StopRecordingAction extends AbstractModelAction {

    public StopRecordingAction(Node source, List<? extends Model> models, Recorder recorder) {
        super(source, models, recorder, new StopRecordingTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't stop recording", "Stopping recording of {0} failed:");
    }
}
