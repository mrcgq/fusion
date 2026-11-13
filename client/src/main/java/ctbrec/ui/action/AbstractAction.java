package ctbrec.ui.action;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import ctbrec.ui.tasks.TaskExecutionException;
import javafx.application.Platform;

public abstract class AbstractAction<P, R> {

    private R result;

    public CompletableFuture<Optional<R>> execute(P param) {
        try {
            result = doExecute(param);
            Platform.runLater(() -> onSuccess(Optional.ofNullable(result)));
            return CompletableFuture.completedFuture(Optional.of(result));
        } catch (Exception e) {
            Platform.runLater(() -> onError(e));
            return CompletableFuture.failedFuture(e);
        } finally {
            Platform.runLater(() -> done(Optional.ofNullable(result)));
        }
    }

    protected abstract R doExecute(P param) throws InvalidKeyException, NoSuchAlgorithmException, IOException;

    public CompletableFuture<Optional<R>> executeAsync(P param) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                result = doExecute(param);
                Platform.runLater(() -> onSuccess(Optional.ofNullable(result)));
                return Optional.of(result);
            } catch (Exception e) {
                Platform.runLater(() -> onError(e));
                throw new TaskExecutionException(e);
            } finally {
                Platform.runLater(() -> done(Optional.ofNullable(result)));
            }
        });
    }

    @SuppressWarnings("unchecked")
    public <T extends AbstractAction<P, R>> T beforeOnGuiThread(Runnable r) {
        return (T) this;
    }

    protected void onSuccess(Optional<R> result) {}

    protected void onError(Exception e) {}

    protected void done(Optional<R> result) {}
}
