package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class ResumeRecordingTask extends AbstractModelTask {

    public ResumeRecordingTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                model.setSuspended(false);
                recorder.resumeRecording(model);
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
