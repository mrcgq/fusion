package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class StartRecordingTask extends AbstractModelTask {

    public StartRecordingTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                model.setMarkedForLaterRecording(false);
                recorder.addModel(model);
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
