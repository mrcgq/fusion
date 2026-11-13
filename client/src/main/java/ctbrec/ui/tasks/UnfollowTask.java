package ctbrec.ui.tasks;

import ctbrec.recorder.Recorder;

public class UnfollowTask extends AbstractModelTask {

    public UnfollowTask(Recorder recorder) {
        super(recorder, model -> {
            try {
                if (model.getSite().login()) {
                    if (!model.unfollow()) {
                        throw new TaskExecutionException(new RuntimeException("Unfollowing " + model.getSite().getName() + " failed"));
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
