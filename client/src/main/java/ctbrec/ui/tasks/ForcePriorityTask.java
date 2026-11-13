package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class ForcePriorityTask extends AbstractModelTask {

    public ForcePriorityTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                model.setForcePriority(true);
                recorder.forcePriorityRecording(model);
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
