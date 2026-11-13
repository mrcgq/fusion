package ctbrec.recorder;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.event.EventBusHolder;
import ctbrec.event.ModelIsOnlineEvent;
import ctbrec.event.ModelStateChangedEvent;
import ctbrec.io.HttpException;
import ctbrec.sites.chaturbate.ChaturbateModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.UNCHECKED;

public class OnlineMonitor extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(OnlineMonitor.class);
    private static final boolean IGNORE_CACHE = true;

    private volatile boolean running = false;
    private final Recorder recorder;

    private final Map<Model, Model.State> states = new HashMap<>();

    private final Map<String, ExecutorService> executors = new HashMap<>();
    private final Config config;

    public OnlineMonitor(Recorder recorder, Config config) {
        this.recorder = recorder;
        this.config = config;
        setName("OnlineMonitor");
        setDaemon(true);
    }

    @Override
    public void run() {
        running = true;
        while (running) {
            Instant begin = Instant.now();
            List<Model> models = recorder.getModels();

            // remove models, which are not recorded anymore
            removeDeletedModels(models);

            // update the currently recorded models
            updateModels(models);

            Instant end = Instant.now();
            Duration timeCheckTook = Duration.between(begin, end);
            suspendUntilNextIteration(models, timeCheckTook);
        }
        LOG.debug("{} terminated", getName());
    }

    private void removeDeletedModels(List<Model> models) {
        states.keySet().removeIf(model -> !models.contains(model));
    }

    private void updateModels(List<Model> models) {
        // sort models by adding date (new go first)
        models.sort((a, b) -> b.getAddedTimestamp().compareTo(a.getAddedTimestamp()));
        // sort models by priority
        models.sort((a, b) -> b.getPriority() - a.getPriority());
        // submit online check jobs to the executor for the model's site
        List<ModelAwareFuture> futures = new LinkedList<>();
        for (Model model : models) {
            boolean skipCheckForSuspended = config.getSettings().onlineCheckSkipsPausedModels && model.isSuspended();
            boolean skipCheckForMarkedAsLater = model.isMarkedForLaterRecording();
            if (skipCheckForSuspended || skipCheckForMarkedAsLater) {
                // force set offline in case model was suspended while online
                // this should be thread safe as we don't access the same model from multiple threads
                setModelStateNotified(model, Model.State.OFFLINE);
            } else {
                futures.add(updateModel(model));
            }
        }
        // wait for all jobs to finish
        for (ModelAwareFuture future : futures) {
            Duration timeout = future.getModel().getSite().getOnlineCheckTimeout();
            try {
                future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                LOG.debug("Online check interrupted for model {}", future.getModel(), e);
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                LOG.info("Error while checking online state for model {}", future.getModel(), e);
            } catch (TimeoutException e) {
                LOG.debug("Online check didn't finish after {}ms for model {}", timeout.toMillis(), future.getModel());
            }
        }
    }
    
    private void setModelStateNotified(Model model, Model.State state) {
        Model.State oldState = states.getOrDefault(model, UNCHECKED);
        states.put(model, state);
        if (state != oldState) {
            EventBusHolder.BUS.post(new ModelStateChangedEvent(model, oldState, state));
        }
    }

    private ModelAwareFuture updateModel(Model model) {
        final String siteName = model.getSite().getName();
        ExecutorService executor = executors.computeIfAbsent(siteName, name -> Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("OnlineMonitorThread-" + siteName);
            t.setPriority(MIN_PRIORITY);
            t.setDaemon(true);
            return t;
        }));

        var future = executor.submit(() -> {
            try {
                if (model.isOnline(IGNORE_CACHE)) {
                    EventBusHolder.BUS.post(new ModelIsOnlineEvent(model));
                    model.setLastSeen(Instant.now());
                }
                Model.State state;
                if (model instanceof ChaturbateModel && model.getOnlineState(true) == OFFLINE) {
                    state = OFFLINE;
                } else {
                    state = model.getOnlineState(false);
                }
                LOG.debug("Model online state: {} {}", model.getName(), state);
                setModelStateNotified(model, state);
            } catch (HttpException e) {
                LOG.error("Couldn't check if model {} is online. HTTP Response: {} - {}", model.getName(), e.getResponseCode(), e.getResponseMessage());
            } catch (SocketTimeoutException e) {
                LOG.error("Couldn't check if model {} is online. Request timed out", model.getName());
            } catch (InterruptedException | InterruptedIOException e) {
                Thread.currentThread().interrupt();
                if (running) {
                    LOG.error("Couldn't check if model {} is online", model.getName(), e);
                }
            } catch (Exception e) {
                LOG.error("Couldn't check if model {} is online", model.getName(), e);
            }
            return model;
        });
        return new ModelAwareFuture(model, future);
    }

    private void suspendUntilNextIteration(List<Model> models, Duration timeCheckTook) {
        LOG.debug("Online check for {} models took {} seconds", models.size(), timeCheckTook.getSeconds());
        long sleepTime = config.getSettings().onlineCheckIntervalInSecs;
        if (timeCheckTook.getSeconds() < sleepTime) {
            try {
                if (running) {
                    long millis = TimeUnit.SECONDS.toMillis(sleepTime - timeCheckTook.getSeconds());
                    LOG.trace("Sleeping {}ms", millis);
                    Thread.sleep(millis);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.trace("Sleep interrupted");
            }
        }
    }

    public void shutdown() {
        running = false;
        for (ExecutorService executor : executors.values()) {
            executor.shutdownNow();
        }
        interrupt();
    }

    private record ModelAwareFuture(Model model, Future<Model> future) implements Future<Model> {

        public Model getModel() {
            return model;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return future.cancel(mayInterruptIfRunning);
        }

        @Override
        public boolean isCancelled() {
            return future.isCancelled();
        }

        @Override
        public boolean isDone() {
            return future.isDone();
        }

        @Override
        public Model get() throws InterruptedException, ExecutionException {
            return future.get();
        }

        @Override
        public Model get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return future.get(timeout, unit);
        }
    }
}
