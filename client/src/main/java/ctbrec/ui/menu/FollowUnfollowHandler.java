package ctbrec.ui.menu;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.action.AbstractModelAction.Result;
import ctbrec.ui.action.FollowAction;
import ctbrec.ui.action.TriConsumer;
import ctbrec.ui.action.UnfollowAction;
import javafx.scene.Node;

public class FollowUnfollowHandler {

    private static final Logger LOG = LoggerFactory.getLogger(FollowUnfollowHandler.class);

    private Node source;
    private Recorder recorder;
    private TriConsumer<Model, Boolean, Boolean> callback;

    public FollowUnfollowHandler(Node source, Recorder recorder, TriConsumer<Model, Boolean, Boolean> callback) {
        this.source = source;
        this.recorder = recorder;
        this.callback = callback;
    }

    protected void follow(List<Model> selectedModels) {
        new FollowAction(source, selectedModels, recorder).execute().thenAccept(r -> {
            r.stream().filter(rs -> rs.getThrowable() == null).map(Result::getModel).forEach(m -> callback.accept(m, true, true));
            r.stream().filter(rs -> rs.getThrowable() != null).map(Result::getModel).forEach(m -> callback.accept(m, true, false));
        }).exceptionally(ex -> {
            LOG.error("Couldn't follow model", ex);
            return null;
        });
    }

    protected void unfollow(List<Model> selectedModels) {
        new UnfollowAction(source, selectedModels, recorder).execute().thenAccept(r -> {
            r.stream().filter(rs -> rs.getThrowable() == null).map(Result::getModel).forEach(m -> callback.accept(m, false, true));
            r.stream().filter(rs -> rs.getThrowable() != null).map(Result::getModel).forEach(m -> callback.accept(m, false, false));
        }).exceptionally(ex -> {
            LOG.error("Couldn't unfollow model", ex);
            return null;
        });
    }
}
