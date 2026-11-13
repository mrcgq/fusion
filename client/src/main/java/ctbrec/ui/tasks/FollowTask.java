package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class FollowTask extends AbstractModelTask {

    public FollowTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                if (model.getSite().login()) {
                    if (!model.follow()) {
                        throw new TaskExecutionException(new RuntimeException("Following " + model.getSite().getName() + " failed"));
                    }
                } else {
                    throw new TaskExecutionException(new RuntimeException("Login to " + model.getSite().getName() + " failed"));
                }
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        });
    }
}
