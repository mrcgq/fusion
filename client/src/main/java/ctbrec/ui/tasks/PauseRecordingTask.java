package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class PauseRecordingTask extends AbstractModelTask {

    public PauseRecordingTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                model.setSuspended(true);
                recorder.suspendRecording(model);
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
