package ctbrec.ui.tabs.recorded;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.JavaFxModel;
import ctbrec.ui.action.AbstractModelAction.Result;
import ctbrec.ui.action.*;
import ctbrec.ui.controls.DateTimeCellFactory;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableColumn.CellEditEvent;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.util.Callback;
import javafx.util.Duration;
import javafx.util.converter.NumberStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static ctbrec.Recording.State.RECORDING;

public class RecordedModelsTab extends AbstractRecordedModelsTab implements TabSelectionListener {
    private static final Logger LOG = LoggerFactory.getLogger(RecordedModelsTab.class);

    private ScheduledService<List<JavaFxModel>> updateService;
    private volatile boolean cellEditing = false;

    Button pauseAll = new Button("Pause All");
    Button resumeAll = new Button("Resume All");
    ToggleButton toggleRecording = new ToggleButton("Pause Recording");
    protected BorderPane root = new BorderPane();

    public RecordedModelsTab(String title, Recorder recorder, List<Site> sites) {
        super(title, "recordedModelsTable");
        this.recorder = recorder;
        this.sites = sites;
        createGui();
        setClosable(false);
        initializeUpdateService();
    }

    @Override
    protected void createGui() {
        super.createGui();

        int idx = 0;

        addPreviewColumn(idx++);
        addPortraitColumn(idx++);
        addModelColumn(idx++);
        addUrlColumn(idx++);

        TableColumn<JavaFxModel, Boolean> online = new TableColumn<>("Online");
        online.setCellValueFactory(cdf -> cdf.getValue().getOnlineProperty());
        online.setCellFactory(param -> new OnlineTableCell());
        online.setPrefWidth(100);
        online.setEditable(false);
        online.setId("online");
        online.setUserData(idx++);
        online.setStyle(STYLE_ALIGN_CENTER);
        columns.add(online);
        table.getColumns().add(online);
        TableColumn<JavaFxModel, Boolean> recording = new TableColumn<>("Recording");
        recording.setCellValueFactory(cdf -> cdf.getValue().getRecordingProperty());
        recording.setCellFactory(tc -> new RecordingTableCell());
        recording.setPrefWidth(100);
        recording.setEditable(false);
        recording.setId("recording");
        recording.setUserData(idx++);
        recording.setStyle(STYLE_ALIGN_CENTER);
        columns.add(recording);
        table.getColumns().add(recording);
        TableColumn<JavaFxModel, Boolean> paused = new TableColumn<>("Paused");
        paused.setCellValueFactory(cdf -> cdf.getValue().getPausedProperty());
        paused.setCellFactory(CheckBoxTableCell.forTableColumn(paused));
        paused.setPrefWidth(100);
        paused.setEditable(true);
        paused.setId("paused");
        paused.setUserData(idx++);
        paused.setStyle(STYLE_ALIGN_CENTER);
        columns.add(paused);
        table.getColumns().add(paused);
        TableColumn<JavaFxModel, Number> priority = new TableColumn<>("Priority");
        priority.setCellValueFactory(param -> param.getValue().getPriorityProperty());
        priority.setCellFactory(new PriorityCellFactory());
        priority.setPrefWidth(90);
        priority.setEditable(true);
        priority.setOnEditStart(e -> cellEditing = true);
        priority.setOnEditCommit(this::onUpdatePriority);
        priority.setOnEditCancel(e -> cellEditing = false);
        priority.setId("priority");
        priority.setUserData(idx++);
        columns.add(priority);
        table.getColumns().add(priority);
        TableColumn<JavaFxModel, Instant> lastSeen = new TableColumn<>("Last seen");
        lastSeen.setCellValueFactory(cdf -> cdf.getValue().lastSeenProperty());
        lastSeen.setCellFactory(new DateTimeCellFactory<>(config.getDateTimeFormatter()));
        lastSeen.setPrefWidth(150);
        lastSeen.setEditable(false);
        lastSeen.setId("lastSeen");
        lastSeen.setUserData(idx++);
        lastSeen.setStyle(STYLE_ALIGN_CENTER);
        columns.add(lastSeen);
        table.getColumns().add(lastSeen);
        TableColumn<JavaFxModel, Instant> lastRecorded = new TableColumn<>("Last recorded");
        lastRecorded.setCellValueFactory(cdf -> cdf.getValue().lastRecordedProperty());
        lastRecorded.setCellFactory(new DateTimeCellFactory<>(config.getDateTimeFormatter()));
        lastRecorded.setPrefWidth(150);
        lastRecorded.setEditable(false);
        lastRecorded.setId("lastRecorded");
        lastRecorded.setUserData(idx++);
        lastRecorded.setStyle(STYLE_ALIGN_CENTER);
        columns.add(lastRecorded);
        table.getColumns().add(lastRecorded);        
        TableColumn<JavaFxModel, Model.State> onlineState = new TableColumn<>("State");
        onlineState.setCellValueFactory(cdf -> cdf.getValue().onlineStateProperty());
        onlineState.setCellFactory(new ClickableCellFactory<>());
        onlineState.setPrefWidth(150);
        onlineState.setEditable(false);
        onlineState.setId("onlineState");
        onlineState.setUserData(idx++);
        onlineState.setStyle(STYLE_ALIGN_CENTER);
        onlineState.setVisible(false);
        columns.add(onlineState);
        table.getColumns().add(onlineState);
        
        addAddedTimestampColumn(idx++);
        addNotesColumn(idx);

        addModelBox.getChildren().add(3, pauseAll);
        addModelBox.getChildren().add(4, resumeAll);
        addModelBox.getChildren().add(5, toggleRecording);
        HBox.setMargin(pauseAll, new Insets(0, 0, 0, 20));
        pauseAll.setOnAction(this::pauseAll);
        resumeAll.setOnAction(this::resumeAll);
        pauseAll.setPadding(new Insets(5));
        resumeAll.setPadding(new Insets(5));
        toggleRecording.setPadding(new Insets(5));
        toggleRecording.setOnAction(this::toggleRecording);
        HBox.setMargin(toggleRecording, new Insets(0, 0, 0, 20));

        checkModelAccountExistance
                .setOnAction(evt -> new CheckModelAccountAction(checkModelAccountExistance, recorder).execute(Predicate.not(Model::isMarkedForLaterRecording)));

        root.setPadding(new Insets(5));
        root.setTop(addModelBox);
        root.setCenter(scrollPane);
        setContent(root);

        restoreState();
    }

    @Override
    protected List<Model> getExportList() {
        return recorder.getModels().stream().filter(Predicate.not(Model::isMarkedForLaterRecording)).toList();
    }

    private void onUpdatePriority(CellEditEvent<JavaFxModel, Number> evt) {
        try {
            int prio = Optional.ofNullable(evt.getNewValue()).map(Number::intValue).orElse(-1);
            JavaFxModel m = evt.getRowValue();
            updatePriority(m, prio);
            table.refresh();
        } finally {
            cellEditing = false;
        }
    }

    private void updatePriority(JavaFxModel model, int priority) {
        try {
            if (priority < 0 || priority > Model.MAX_PRIO) {
                var msg = "Priority has to be between 0 and " + Model.MAX_PRIO;
                Dialogs.showError(table.getScene(), "Invalid value", msg, null);
            } else {
                model.setPriority(priority);
                recorder.priorityChanged(model.getDelegate());
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
            Dialogs.showError(table.getScene(), "Couldn't update priority", e.getMessage(), e);
        }
    }

    protected void pauseAll(ActionEvent evt) {
        boolean yes = Dialogs.showConfirmDialog("Pause all models", "", "Pause the recording of all models?", getTabPane().getScene());
        if (yes) {
            List<Model> models = recorder.getModels().stream().filter(Predicate.not(Model::isMarkedForLaterRecording)).toList();
            new PauseAction(getTabPane(), models, recorder).execute();
        }
    }

    protected void resumeAll(ActionEvent evt) {
        boolean yes = Dialogs.showConfirmDialog("Resume all models", "", "Resume the recording of all models?", getTabPane().getScene());
        if (yes) {
            List<Model> models = recorder.getModels().stream().filter(Predicate.not(Model::isMarkedForLaterRecording)).toList();
            new ResumeAction(getTabPane(), models, recorder).execute();
        }
    }

    private void toggleRecording(ActionEvent evt) {
        new ToggleRecordingAction(toggleRecording, recorder).execute();
    }

    void initializeUpdateService() {
        updateService = createUpdateService();
        updateService.setPeriod(new Duration(TimeUnit.SECONDS.toMillis(2)));
        updateService.setOnSucceeded(this::onUpdateSuccess);
        updateService.setOnFailed(event -> LOG.info("Couldn't get list of models from recorder", event.getSource().getException()));
    }

    private void onUpdateSuccess(WorkerStateEvent event) {
        if (cellEditing) {
            return;
        }

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
                updatedModel.getPausedProperty().addListener(createPauseListener(updatedModel));
                updatedModel.getForcePriorityProperty().addListener(createForcePriorityListener(updatedModel));
            } else {
                // make sure to update the JavaFX online property, so that the table cell is updated
                JavaFxModel oldModel = observableModels.get(index);
                oldModel.updateFrom(updatedModel);
            }
        }
    }

    private ChangeListener<Boolean> createPauseListener(JavaFxModel updatedModel) {
        return (obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                if (!recorder.isSuspended(updatedModel)) {
                    pauseRecording(Collections.singletonList(updatedModel));
                }
            } else {
                if (recorder.isSuspended(updatedModel)) {
                    resumeRecording(Collections.singletonList(updatedModel));
                }
            }
        };
    }

    private ChangeListener<Boolean> createForcePriorityListener(JavaFxModel updatedModel) {
        return (obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV)) {
                if (!recorder.isForcePriority(updatedModel)) {
                    forcePriority(Collections.singletonList(updatedModel));
                }
            } else {
                if (recorder.isForcePriority(updatedModel)) {
                    resumePriority(Collections.singletonList(updatedModel));
                }
            }
        };
    }

    private ScheduledService<List<JavaFxModel>> createUpdateService() {
        ScheduledService<List<JavaFxModel>> modelUpdateService = new ScheduledService<>() {
            @Override
            protected Task<List<JavaFxModel>> createTask() {
                return new Task<>() {
                    @Override
                    public List<JavaFxModel> call() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
                        LOG.trace("Updating recorded models");
                        List<Recording> recordings = recorder.getRecordings();
                        List<Model> onlineModels = recorder.getOnlineModels();
                        return recorder.getModels().stream().filter(Predicate.not(Model::isMarkedForLaterRecording)).map(JavaFxModel::new).peek(fxm -> { // NOSONAR
                            for (Recording recording : recordings) {
                                if (recording.getStatus() == RECORDING && Objects.equals(recording.getModel(), fxm)) {
                                    fxm.setRecordingProperty(true);
                                    break;
                                }
                            }

                            for (Model onlineModel : onlineModels) {
                                if (Objects.equals(onlineModel, fxm)) {
                                    fxm.setOnlineProperty(true);
                                    try {
                                        fxm.setOnlineStateProperty(onlineModel.getOnlineState(true));
                                    } catch (Exception e) {}
                                    break;
                                }
                            }
                        }).toList();
                    }
                };
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName("RecordedModelsTab UpdateService");
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
            String header = "This will stop the recording of " + n + " model" + plural;
            confirmed = Dialogs.showConfirmDialog("Stop Recording", "Continue?", header, table.getScene());
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

    private void pauseRecording(List<JavaFxModel> selectedModels) {
        List<Model> models = selectedModels.stream().map(JavaFxModel::getDelegate).toList();
        new PauseAction(getTabPane(), models, recorder).execute();
    }

    private void resumeRecording(List<JavaFxModel> selectedModels) {
        List<Model> models = selectedModels.stream().map(JavaFxModel::getDelegate).toList();
        new ResumeAction(getTabPane(), models, recorder).execute();
    }

    private void forcePriority(List<JavaFxModel> selectedModels) {
        List<Model> models = selectedModels.stream().map(JavaFxModel::getDelegate).toList();
        new ForcePriorityAction(getTabPane(), models, recorder).execute();
    }

    private void resumePriority(List<JavaFxModel> selectedModels) {
        List<Model> models = selectedModels.stream().map(JavaFxModel::getDelegate).toList();
        new ResumePriorityAction(getTabPane(), models, recorder).execute();
    }

    private class PriorityCellFactory implements Callback<TableColumn<JavaFxModel, Number>, TableCell<JavaFxModel, Number>> {
        @Override
        public TableCell<JavaFxModel, Number> call(TableColumn<JavaFxModel, Number> param) {
            Callback<TableColumn<JavaFxModel, Number>, TableCell<JavaFxModel, Number>> callback = TextFieldTableCell.forTableColumn(new NumberStringConverter());
            TableCell<JavaFxModel, Number> tableCell = callback.call(param);

            tableCell.setOnScroll(event -> {
                if (event.isControlDown()) {
                    event.consume();
                    JavaFxModel m = tableCell.getTableRow().getItem();
                    int prio = m.getPriority();
                    if (event.getDeltaY() < 0) {
                        prio--;
                    } else {
                        prio++;
                    }
                    prio = Math.min(Math.max(0, prio), Model.MAX_PRIO);
                    m.setPriority(prio);
                    updatePriority(m, prio);
                }
            });
            tableCell.setStyle("-fx-alignment: CENTER-LEFT;");
            return tableCell;
        }
    }

    @Override
    boolean getMarkModelsForLaterRecording() {
        return false;
    }
}
