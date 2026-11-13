package ctbrec.ui.action;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tasks.AbstractModelTask;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;

public abstract class AbstractModelAction {

    protected Node source;
    protected List<? extends Model> models;
    protected Recorder recorder;
    private AbstractModelTask task;

    protected AbstractModelAction(Node source, List<? extends Model> models, Recorder recorder, AbstractModelTask task) {
        this.source = source;
        this.models = models;
        this.recorder = recorder;
        this.task = task;
    }

    protected CompletableFuture<List<Result>> execute(String errorHeader, String errorMsg) {
        source.setCursor(Cursor.WAIT);
        return CompletableFuture.supplyAsync(() -> internalExecute(errorHeader, errorMsg), GlobalThreadPool.get());
    }

    protected CompletableFuture<List<Result>> executeSync(String errorHeader, String errorMsg) {
        source.setCursor(Cursor.WAIT);
        return CompletableFuture.completedFuture(internalExecute(errorHeader, errorMsg));
    }

    protected List<Result> internalExecute(String errorHeader, String errorMsg) {
        final List<Result> result = new ArrayList<>(models.size());
        final List<CompletableFuture<Model>> futures = new ArrayList<>(models.size());
        for (Model model : models) {
            futures.add(task
                    .executeSync(model)
                    .whenComplete((mdl, ex) ->
                    result.add(new Result(model, ex))));
        }
        Platform.runLater(() -> source.setCursor(Cursor.DEFAULT));
        checkResultForErrors(errorHeader, errorMsg, result);
        return result;
    }

    protected void checkResultForErrors(String errorHeader, String errorMsg, List<Result> result) {
        List<Result> failed = result.stream().filter(Result::failed).collect(Collectors.toList());
        if (!failed.isEmpty()) {
            Throwable t = failed.get(0).getThrowable();
            String failedModelList = failed.stream().map(Result::getModel).map(Model::getDisplayName).collect(Collectors.joining(", "));
            String msg = MessageFormat.format(errorMsg, failedModelList);
            Dialogs.showError(source.getScene(), errorHeader, msg, t);
        }
    }

    public static class Result {
        private Model model;
        private Throwable throwable;

        public Result(Model model, Throwable t) {
            this.model = model;
            this.throwable = t;
        }

        public boolean successful() {
            return throwable == null;
        }

        public boolean failed() {
            return throwable != null;
        }

        public Model getModel() {
            return model;
        }

        public Throwable getThrowable() {
            return throwable;
        }
    }
}
