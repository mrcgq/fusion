package ctbrec.ui.tasks;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.recorder.Recorder;

public abstract class AbstractModelTask {
    protected Recorder recorder;
    private  Consumer<Model> concreteTask;

    protected AbstractModelTask(Recorder recorder, Consumer<Model> concreteTask) {
        this.recorder = recorder;
        this.concreteTask = concreteTask;
    }

    public CompletableFuture<Model> executeSync(Model model) {
        try {
            concreteTask.accept(model);
            return CompletableFuture.completedFuture(model);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    public CompletableFuture<Model> execute(Model model) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                concreteTask.accept(model);
                return model;
            } catch (Exception e) {
                throw new TaskExecutionException(e);
            }
        }, GlobalThreadPool.get());
    }
}
