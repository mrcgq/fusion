package ctbrec.ui.action;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.Node;

public class ModelMassEditAction {

    private static final Logger LOG = LoggerFactory.getLogger(ModelMassEditAction.class);

    protected List<? extends Model> models;
    protected Consumer<Model> action;
    protected Node source;

    protected ModelMassEditAction() {
    }

    protected ModelMassEditAction(Node source, List<? extends Model> models) {
        this.source = source;
        this.models = models;
    }

    public ModelMassEditAction(Node source, List<? extends Model> models, Consumer<Model> action) {
        this.source = source;
        this.models = models;
        this.action = action;
    }

    public void execute() {
        execute(m -> {});
    }

    public void executeSync(Consumer<Model> callback) {
        Platform.runLater(() -> source.setCursor(Cursor.WAIT));
        Consumer<Model> cb = Objects.requireNonNull(callback, "Callback is null, call execute() instead");
        List<Future<?>> futures = new LinkedList<>();
        for (Model model : getModels()) {
            futures.add(GlobalThreadPool.submit(() -> {
                action.accept(model);
                cb.accept(model);
            }));
        }
        Exception ex = null;
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                ex = e;
            }
        }
        if (ex != null) {
            LOG.error("Error while executing model mass edit", ex);
            Dialogs.showError(source.getScene(), "Error", "Error while execution action", ex);
        }
        Platform.runLater(() -> source.setCursor(Cursor.DEFAULT));
    }

    public void execute(Consumer<Model> callback) {
        GlobalThreadPool.submit(() -> executeSync(callback));
    }

    @SuppressWarnings("unchecked")
    protected List<Model> getModels() {
        return (List<Model>) models;
    }
}
