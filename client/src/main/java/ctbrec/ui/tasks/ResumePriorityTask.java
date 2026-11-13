package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class ResumePriorityTask extends AbstractModelTask {

    public ResumePriorityTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                model.setForcePriority(false);
                recorder.resumePriorityRecording(model);
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
