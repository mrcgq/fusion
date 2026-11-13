package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class StopRecordingTask extends AbstractModelTask {

    public StopRecordingTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                recorder.stopRecording(model);
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
