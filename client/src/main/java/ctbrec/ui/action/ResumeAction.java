package ctbrec.ui.action;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.tasks.ResumeRecordingTask;
import javafx.scene.Node;

public class ResumeAction extends AbstractModelAction {

    public ResumeAction(Node source, List<Model> models, Recorder recorder) {
        super(source, models, recorder, new ResumeRecordingTask(recorder));
    }

    public CompletableFuture<List<Result>> execute() {
        return super.execute("Couldn't resume recording", "Resuming recording of {0} failed:");
    }
}
