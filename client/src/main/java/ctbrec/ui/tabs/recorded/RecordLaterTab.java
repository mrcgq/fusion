package ctbrec.ui.tabs.recorded;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.JavaFxModel;
import ctbrec.ui.action.AbstractModelAction.Result;
import ctbrec.ui.action.CheckModelAccountAction;
import ctbrec.ui.action.StopRecordingAction;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.application.Platform;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.scene.layout.BorderPane;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

public class RecordLaterTab extends AbstractRecordedModelsTab implements TabSelectionListener {
    private static final Logger LOG = LoggerFactory.getLogger(RecordLaterTab.class);

    private ScheduledService<List<JavaFxModel>> updateService;

    public RecordLaterTab(String title, Recorder recorder, List<Site> sites) {
        super(title, "recordLaterTable");
        this.recorder = recorder;
        this.sites = sites;
        createGui();
        setClosable(false);
        initializeUpdateService();
    }

    @Override
    protected void createGui() {
        super.createGui();

        int columnIdx = 0;
        addPreviewColumn(columnIdx++);
        addPortraitColumn(columnIdx++);
        addModelColumn(columnIdx++);
        addUrlColumn(columnIdx++);
        addAddedTimestampColumn(columnIdx++);
        addNotesColumn(columnIdx);

        var root = new BorderPane();
        root.setPadding(new Insets(5));
        root.setTop(addModelBox);
        root.setCenter(scrollPane);
        setContent(root);

        checkModelAccountExistance
        .setOnAction(evt -> new CheckModelAccountAction(checkModelAccountExistance, recorder).execute(Model::isMarkedForLaterRecording));

        restoreState();
    }

    @Override
    protected List<Model> getExportList() {
        return recorder.getModels().stream().filter(Model::isMarkedForLaterRecording).toList();
    }

    void initializeUpdateService() {
        updateService = createUpdateService();
        updateService.setPeriod(new Duration(TimeUnit.SECONDS.toMillis(2)));
        updateService.setOnSucceeded(this::onUpdateSuccess);
        updateService.setOnFailed(event -> LOG.info("Couldn't get list of models from recorder", event.getSource().getException()));
    }

    private void onUpdateSuccess(WorkerStateEvent event) {
        List<JavaFxModel> updatedModels = updateService.getValue();
        if (updatedModels == null) {
            return;
        }

        lock.lock();
        try {
            addOrUpdateModels(updatedModels);

            // remove old ones, which are not in the list of updated models
            observableModels.removeIf(Predicate.not(updatedModels::contains));
        } finally {
            lock.unlock();
        }

        filteredModels.clear();
        filter(filterTextField.getText());
        table.sort();
        table.refresh();
    }

    private void addOrUpdateModels(List<JavaFxModel> updatedModels) {
        for (JavaFxModel updatedModel : updatedModels) {
            int index = observableModels.indexOf(updatedModel);
            if (index == -1) {
                observableModels.add(updatedModel);
            } else {
                // make sure to update the JavaFX online property, so that the table cell is updated
                JavaFxModel oldModel = observableModels.get(index);
                oldModel.updateFrom(updatedModel);
            }
        }
    }

    private ScheduledService<List<JavaFxModel>> createUpdateService() {
        ScheduledService<List<JavaFxModel>> modelUpdateService = new ScheduledService<>() {
            @Override
            protected Task<List<JavaFxModel>> createTask() {
                return new Task<>() {
                    @Override
                    public List<JavaFxModel> call() {
                        LOG.trace("Updating models marked for later recording");
                        return recorder.getModels().stream().filter(Model::isMarkedForLaterRecording).map(JavaFxModel::new).toList();
                    }
                };
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName("RecordLaterTab UpdateService");
            return t;
        });
        modelUpdateService.setExecutor(executor);
        return modelUpdateService;
    }

    @Override
    public void selected() {
        if (updateService != null) {
            updateService.reset();
            updateService.restart();
        }
    }

    @Override
    public void deselected() {
        if (updateService != null) {
            updateService.cancel();
        }
    }

    @Override
    void stopAction(List<JavaFxModel> selectedModels) {
        var confirmed = true;
        if (Config.getInstance().getSettings().confirmationForDangerousActions) {
            int n = selectedModels.size();
            String plural = n > 1 ? "s" : "";
            String header = "This will remove " + n + " model" + plural;
            confirmed = Dialogs.showConfirmDialog("Remove From List", "Continue?", header, table.getScene());
        }
        if (confirmed) {
            List<Model> models = selectedModels.stream().map(JavaFxModel::getDelegate).toList();
            new StopRecordingAction(getTabPane(), models, recorder).execute().whenComplete((r, ex) ->
                r.stream().map(Result::getModel).forEach(m -> Platform.runLater(() -> {
                    table.getSelectionModel().clearSelection(table.getItems().indexOf(m));
                    table.getItems().remove(m);
                }))
            );
            portraitCache.invalidateAll(models);
        }
    }

    @Override
    boolean getMarkModelsForLaterRecording() {
        return true;
    }
}
