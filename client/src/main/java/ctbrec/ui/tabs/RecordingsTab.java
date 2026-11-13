package ctbrec.ui.tabs;

import ctbrec.*;
import ctbrec.Recording.State;
import ctbrec.event.EventBusHolder;
import ctbrec.event.RecordingStateChangedEvent;
import ctbrec.io.UrlUtil;
import ctbrec.notes.ModelNotesService;
import ctbrec.recorder.ProgressListener;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.postprocessing.PostProcessingContext;
import ctbrec.ui.*;
import ctbrec.ui.action.PauseAction;
import ctbrec.ui.action.PlayAction;
import ctbrec.ui.action.StopRecordingAction;
import ctbrec.ui.controls.CustomMouseBehaviorContextMenu;
import ctbrec.ui.controls.DateTimeCellFactory;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.Toast;
import ctbrec.ui.controls.table.SettingTableViewStateStore;
import ctbrec.ui.controls.table.StatePersistingTableView;
import ctbrec.ui.menu.ModelMenuContributor;
import ctbrec.ui.tabs.recorded.ModelName;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Font;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.NoSuchFileException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static ctbrec.Recording.State.*;
import static javafx.scene.control.ButtonType.NO;
import static javafx.scene.control.ButtonType.YES;

public class RecordingsTab extends Tab implements TabSelectionListener, ShutdownListener {
    private static final String ERROR_WHILE_DOWNLOADING_RECORDING = "Error while downloading recording";

    private static final Logger LOG = LoggerFactory.getLogger(RecordingsTab.class);

    private ScheduledService<List<JavaFxRecording>> updateService;
    private final Config config;
    private final ModelNotesService modelNotesService;
    private final Recorder recorder;
    private long spaceTotal = -1;
    private long spaceFree = -1;

    FlowPane grid = new FlowPane();
    ScrollPane scrollPane = new ScrollPane();
    SettingTableViewStateStore tableStateStore = new SettingTableViewStateStore(Config.getInstance(), "recordingsTable");
    StatePersistingTableView<JavaFxRecording> table = new StatePersistingTableView<>(tableStateStore);
    ObservableList<JavaFxRecording> observableRecordings = FXCollections.observableArrayList();
    ContextMenu popup;
    ProgressBar spaceLeft;
    Label spaceFreeLabel;
    Label spaceUsedValue;
    Lock recordingsLock = new ReentrantLock();

    public RecordingsTab(String title, Recorder recorder, Config config, ModelNotesService modelNotesService) {
        super(title);
        this.recorder = recorder;
        this.config = config;
        this.modelNotesService = modelNotesService;
        createGui();
        setClosable(false);
        initializeUpdateService();
    }

    @SuppressWarnings("unchecked")
    private void createGui() {
        grid.setPadding(new Insets(5));
        grid.setHgap(5);
        grid.setVgap(5);

        scrollPane.setContent(grid);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        BorderPane.setMargin(scrollPane, new Insets(5));

        table.setEditable(false);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        if (!config.getSettings().showGridLinesInTables) {
            table.setStyle("-fx-table-cell-border-color: transparent;");
        }

        TableColumn<JavaFxRecording, ModelName> name = new TableColumn<>("Model");
        name.setId("name");
        name.setPrefWidth(200);
        name.setCellValueFactory(param -> {
            var modelName = new ModelName(param.getValue().getModel(), recorder);
            return new SimpleObjectProperty<>(modelName);
        });
        TableColumn<JavaFxRecording, Instant> date = new TableColumn<>("Date");
        date.setId("date");
        date.setCellValueFactory(cdf -> {
            var instant = cdf.getValue().getStartDate();
            return new SimpleObjectProperty<>(instant);
        });
        date.setCellFactory(new DateTimeCellFactory<>(config.getDateTimeFormatter()));
        date.setPrefWidth(200);
        TableColumn<JavaFxRecording, String> status = new TableColumn<>("Status");
        status.setId("status");
        status.setCellValueFactory(cdf -> cdf.getValue().getStatusProperty());
        status.setPrefWidth(300);
        TableColumn<JavaFxRecording, String> progress = new TableColumn<>("Progress");
        progress.setId("progress");
        progress.setCellValueFactory(cdf -> cdf.getValue().getProgressProperty());
        progress.setPrefWidth(100);
        TableColumn<JavaFxRecording, Number> size = new TableColumn<>("Size");
        size.setId("size");
        size.setStyle("-fx-alignment: CENTER-RIGHT;");
        size.setPrefWidth(100);
        size.setCellValueFactory(cdf -> cdf.getValue().getSizeProperty());
        size.setCellFactory(tc -> createSizeCell());
        TableColumn<JavaFxRecording, Number> resolution = new TableColumn<>("Resolution");
        resolution.setId("resolution");
        resolution.setPrefWidth(100);
        resolution.setCellValueFactory(cdf -> new SimpleIntegerProperty(cdf.getValue().getSelectedResolution()));
        resolution.setCellFactory(tc -> createResolutionCell());

        TableColumn<JavaFxRecording, String> siteName = new TableColumn<>("Site");
        siteName.setId("siteName");
        siteName.setPrefWidth(200);
        siteName.setCellValueFactory(cdf -> {
            var sname = cdf.getValue().getModel().getSite().getName();
            return new SimpleObjectProperty<>(sname);
        });

        TableColumn<JavaFxRecording, String> notes = new TableColumn<>("Notes");
        notes.setId("notes");
        notes.setPrefWidth(400);
        notes.setCellValueFactory(cdf -> cdf.getValue().getNoteProperty());
        TableColumn<JavaFxRecording, String> modelNotes = new TableColumn<>("Model Notes");
        modelNotes.setId("modelNotes");
        modelNotes.setPrefWidth(400);
        modelNotes.setCellValueFactory(cdf -> {
            String modelNts;
            try {
                modelNts = modelNotesService.loadModelNotes(cdf.getValue().getModel().getUrl()).orElse("");
            } catch (IOException e) {
                LOG.warn("Could not load model notes", e);
                modelNts = "";
            }
            return new SimpleStringProperty(modelNts);
        });

        table.getColumns().addAll(siteName, name, date, status, progress, size, resolution, notes, modelNotes);
        table.setItems(observableRecordings);
        table.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, this::onContextMenuRequested);
        table.addEventHandler(MouseEvent.MOUSE_PRESSED, this::onMousePressed);
        table.addEventFilter(MouseEvent.MOUSE_CLICKED, this::onMouseClicked);
        table.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        scrollPane.setContent(table);

        var spaceBox = new HBox(5);
        var spaceLeftLabel = new Label("Space left on device");
        spaceBox.getChildren().add(spaceLeftLabel);
        spaceLeft = new ProgressBar(0);
        spaceLeft.setPrefSize(200, 22);
        spaceFreeLabel = new Label();
        spaceFreeLabel.setFont(Font.font(11));
        var stack = new StackPane(spaceLeft, spaceFreeLabel);
        var spaceUsedLabel = new Label("Space used:");
        spaceUsedValue = new Label();
        spaceBox.getChildren().addAll(stack, spaceUsedLabel, spaceUsedValue);
        HBox.setMargin(spaceLeftLabel, new Insets(2, 0, 0, 0));
        HBox.setMargin(spaceUsedLabel, new Insets(2, 0, 0, 20));
        HBox.setMargin(spaceUsedValue, new Insets(2, 0, 0, 0));
        BorderPane.setMargin(spaceBox, new Insets(5));

        var root = new BorderPane();
        root.setPadding(new Insets(5));
        root.setTop(spaceBox);
        root.setCenter(scrollPane);
        setContent(root);

        table.restoreState();
    }

    public boolean isDownloadRunning() {
        return observableRecordings.stream().map(Recording::getStatus).anyMatch(s -> s == DOWNLOADING);
    }

    private TableCell<JavaFxRecording, Number> createSizeCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Number sizeInByte, boolean empty) {
                if (empty || sizeInByte == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    setText(StringUtil.formatSize(sizeInByte));
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                    if (Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
                        int row = this.getTableRow().getIndex();
                        JavaFxRecording rec = tableViewProperty().get().getItems().get(row);
                        if (!rec.valueChanged() && rec.getStatus() == RECORDING) {
                            setStyle("-fx-alignment: CENTER-RIGHT; -fx-background-color: red");
                        }
                    }
                }
            }
        };
    }

    private TableCell<JavaFxRecording, Number> createResolutionCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(Number resolution, boolean empty) {
                if (empty || resolution == null) {
                    setText(null);
                    setStyle(null);
                } else {
                    String text = String.valueOf(resolution);
                    if (resolution.intValue() <= 0) {
                        text = "n/a";
                    }
                    if (resolution.intValue() == StreamSource.UNKNOWN) {
                        text = "unknown";
                    }
                    setStyle("-fx-alignment: CENTER-RIGHT;");
                    setText(text);
                }
            }
        };
    }

    private void onContextMenuRequested(ContextMenuEvent event) {
        List<JavaFxRecording> recordings = table.getSelectionModel().getSelectedItems();
        if (recordings != null && !recordings.isEmpty()) {
            popup = createContextMenu(recordings);
            if (!popup.getItems().isEmpty()) {
                popup.show(table, event.getScreenX(), event.getScreenY());
            }
        }
        event.consume();
    }

    private void onMousePressed(MouseEvent event) {
        if (popup != null) {
            popup.hide();
        }
    }

    private void onMouseClicked(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            Recording recording = table.getSelectionModel().getSelectedItem();
            if (recording != null) {
                var state = recording.getStatus();
                if (state == FINISHED || state == RECORDING) {
                    play(recording);
                }
            }
        }
    }

    private void onKeyPressed(KeyEvent event) {
        List<JavaFxRecording> recordings = table.getSelectionModel().getSelectedItems();
        if (recordings != null && !recordings.isEmpty()) {
            State status = recordings.get(0).getStatus();
            if (event.getCode() == KeyCode.DELETE) {
                if (recordings.size() > 1 || status == FINISHED || status == FAILED || status == WAITING) {
                    delete(recordings);
                }
            } else if (event.getCode() == KeyCode.ENTER && status == FINISHED) {
                play(recordings.get(0));
            } else {
                jumpToNextModel(event.getCode());
            }
        }
    }

    void initializeUpdateService() {
        updateService = createUpdateService();
        updateService.setPeriod(new Duration(TimeUnit.SECONDS.toMillis(2)));
        updateService.setOnSucceeded(event -> {
            updateRecordingsTable();
            updateFreeSpaceDisplay();
            updateUsedSpaceDisplay();
        });
        updateService.setOnFailed(event -> {
            LOG.info("Couldn't get list of recordings from recorder", event.getSource().getException());
            var autosizeAlert = new AutosizeAlert(AlertType.ERROR, getTabPane().getScene());
            autosizeAlert.setTitle("Whoopsie!");
            autosizeAlert.setHeaderText("Recordings not available");
            autosizeAlert.setContentText("An error occurred while retrieving the list of recordings");
            autosizeAlert.showAndWait();
        });
    }

    private void updateUsedSpaceDisplay() {
        long sum = table.getItems().stream().mapToLong(Recording::getSizeInByte).sum();
        spaceUsedValue.setText(StringUtil.formatSize(sum));
    }

    private void updateFreeSpaceDisplay() {
        if (spaceTotal != -1 && spaceFree != -1) {
            double free = ((double) spaceFree) / spaceTotal;
            spaceLeft.setProgress(free);
            double totalGiB = ((double) spaceTotal) / 1024 / 1024 / 1024;
            double freeGiB = ((double) spaceFree) / 1024 / 1024 / 1024;
            var df = new DecimalFormat("0.00");
            String tt = df.format(freeGiB) + " / " + df.format(totalGiB) + " GiB";
            spaceLeft.setTooltip(new Tooltip(tt));
            spaceFreeLabel.setText(tt);
        }
    }

    private void updateRecordingsTable() {
        List<JavaFxRecording> recordings = updateService.getValue();
        if (recordings == null) {
            return;
        }

        recordingsLock.lock();
        try {
            // remove deleted recordings
            observableRecordings.removeIf(old -> !recordings.contains(old));

            for (JavaFxRecording recording : recordings) {
                if (!observableRecordings.contains(recording)) {
                    // add new recordings
                    observableRecordings.add(recording);
                } else {
                    // update existing ones
                    int index = observableRecordings.indexOf(recording);
                    JavaFxRecording old = observableRecordings.get(index);
                    old.update(recording);
                }
            }
        } finally {
            recordingsLock.unlock();
        }
        table.sort();
    }

    private ScheduledService<List<JavaFxRecording>> createUpdateService() {
        ScheduledService<List<JavaFxRecording>> service = new ScheduledService<>() {
            @Override
            protected Task<List<JavaFxRecording>> createTask() {
                return new Task<>() {
                    @Override
                    public List<JavaFxRecording> call() throws IOException, InvalidKeyException, NoSuchAlgorithmException {
                        updateSpace();

                        List<JavaFxRecording> recordings = new ArrayList<>();
                        for (Recording rec : recorder.getRecordings()) {
                            recordings.add(new JavaFxRecording(rec));
                        }
                        return recordings;
                    }

                    private void updateSpace() {
                        try {
                            spaceTotal = recorder.getTotalSpaceBytes();
                            spaceFree = recorder.getFreeSpaceBytes();
                            Platform.runLater(() -> spaceLeft.setTooltip(new Tooltip()));
                        } catch (NoSuchFileException e) {
                            // recordings dir does not exist
                            Platform.runLater(() -> spaceLeft.setTooltip(new Tooltip("Recordings directory does not exist")));
                        } catch (IOException e) {
                            LOG.error("Couldn't update free space", e);
                        }
                    }
                };
            }
        };
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName("RecordingsTab UpdateService");
            return t;
        });
        service.setExecutor(executor);
        return service;
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

    private ContextMenu createContextMenu(List<JavaFxRecording> recordings) {
        ContextMenu contextMenu = new CustomMouseBehaviorContextMenu();
        contextMenu.setHideOnEscape(true);
        contextMenu.setAutoHide(true);
        contextMenu.setAutoFix(true);

        JavaFxRecording first = recordings.get(0);
        var openInPlayer = new MenuItem("Open in Player");
        openInPlayer.setOnAction(e -> play(first));
        if (first.getStatus() == RECORDING || first.getStatus() == FINISHED) {
            contextMenu.getItems().add(openInPlayer);
        }
        if (first.getStatus() == RECORDING) {
            var openLiveStream = new MenuItem("Open live stream");
            openLiveStream.setOnAction(e -> play(first.getModel()));
            contextMenu.getItems().add(openLiveStream);
        }

        var openContactSheet = new MenuItem("Open contact sheet");
        openContactSheet.setOnAction(e -> openContactSheet(first));
        openContactSheet.setDisable(first.getContactSheet().isEmpty());
        contextMenu.getItems().add(openContactSheet);

        var stopRecording = new MenuItem("Stop Recording");
        stopRecording.setOnAction(e -> stopRecording(recordings.stream().map(JavaFxRecording::getModel).collect(Collectors.toList())));
        if (recordings.stream().anyMatch(r -> r.getStatus() == RECORDING)) {
            contextMenu.getItems().add(stopRecording);
        }

        var pauseRecording = new MenuItem("Pause Recording");
        pauseRecording.setOnAction(e -> pauseRecording(recordings.stream().map(JavaFxRecording::getModel).collect(Collectors.toList())));
        if (recordings.stream().anyMatch(r -> r.getStatus() == RECORDING)) {
            contextMenu.getItems().add(pauseRecording);
        }

        var deleteRecording = new MenuItem("Delete");
        deleteRecording.setOnAction(e -> delete(recordings));
        if (first.getStatus() == FINISHED || first.getStatus() == WAITING || first.getStatus() == FAILED || recordings.size() > 1) {
            contextMenu.getItems().add(deleteRecording);
            deleteRecording.setDisable(recordings.stream().allMatch(Recording::isPinned));
        }

        var tmp = new CustomMouseBehaviorContextMenu();
        ModelMenuContributor.newContributor(getTabPane(), Config.getInstance(), recorder) //
                .withStartStopCallback(m -> getTabPane().setCursor(Cursor.DEFAULT)) //
                .removeModelAfterIgnore(true) //
                .afterwards(table::refresh) //
                .contributeToMenu(List.of(recordings.get(0).getModel()), tmp);
        var modelSubMenu = new Menu("Model");
        modelSubMenu.getItems().addAll(tmp.getItems());
        contextMenu.getItems().add(modelSubMenu);

        var openDir = new MenuItem("Open directory");
        openDir.setOnAction(e -> onOpenDirectory(first));
        if (Config.getInstance().getSettings().localRecording) {
            contextMenu.getItems().add(openDir);
        }

        var downloadRecording = new MenuItem("Download");
        downloadRecording.setOnAction(e -> download(first));
        if (!Config.getInstance().getSettings().localRecording && first.getStatus() == FINISHED) {
            contextMenu.getItems().add(downloadRecording);
        }

        var notes = new MenuItem("Notes");
        notes.setOnAction(e -> notes(first));
        contextMenu.getItems().add(notes);

        if (first.isPinned()) {
            var unpinRecording = new MenuItem("Unpin");
            unpinRecording.setOnAction(e -> unpin(recordings));
            contextMenu.getItems().add(unpinRecording);
        } else {
            var pinRecording = new MenuItem("Pin");
            pinRecording.setOnAction(e -> pin(recordings));
            contextMenu.getItems().add(pinRecording);
        }

        var rerunPostProcessing = new MenuItem("Rerun Post-Processing");
        rerunPostProcessing.setOnAction(e -> triggerPostProcessing(recordings));
        contextMenu.getItems().add(rerunPostProcessing);
        rerunPostProcessing.setDisable(!recordings.stream().allMatch(Recording::canBePostProcessed));

        if (recordings.size() > 1) {
            openInPlayer.setDisable(true);
            openDir.setDisable(true);
        }

        return contextMenu;
    }

    private void stopRecording(List<Model> selectedModels) {
        var confirmed = true;
        if (Config.getInstance().getSettings().confirmationForDangerousActions) {
            int n = selectedModels.size();
            String plural = n > 1 ? "s" : "";
            String header = "This will stop the recording of " + n + " model" + plural;
            confirmed = Dialogs.showConfirmDialog("Stop Recording", "Continue?", header, table.getScene());
        }
        if (confirmed) {
            new StopRecordingAction(getTabPane(), selectedModels, recorder).execute();
        }
    }

    private void pauseRecording(List<Model> selectedModels) {
        new PauseAction(getTabPane(), selectedModels, recorder).execute();
    }

    private void openContactSheet(JavaFxRecording recording) {
        if (config.getSettings().localRecording) {
            recording.getContactSheet().ifPresent(f -> GlobalThreadPool.submit(() -> DesktopIntegration.open(f)));
        } else {
            recording.getContactSheet().ifPresent(f -> GlobalThreadPool.submit(() -> {
                File target;
                try {
                    target = File.createTempFile("cs_", ".jpg");
                    target.deleteOnExit();
                    var download = new FileDownload(CamrecApplication.httpClient, p -> {
                        if (p == 100) {
                            DesktopIntegration.open(target);
                        }
                    });
                    String url = config.getServerUrl() + "/hls/" + recording.getId() + f.getCanonicalPath();
                    if (config.getSettings().requireAuthentication) {
                        url = UrlUtil.addHmac(url, config);
                    }
                    download.start(new URL(url), target);
                } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
                    Dialogs.showError(getTabPane().getScene(), "Download Error", "An error occurred while downloading the contact sheet", e);
                }
            }));
        }
    }

    private void notes(JavaFxRecording recording) {
        Node source = getTabPane();
        String notes = recording.getNote();
        Optional<String> newNote = Dialogs.showTextInput(source.getScene(), "Recording Notes", "", notes);
        if (newNote.isPresent()) {
            table.setCursor(Cursor.WAIT);
            GlobalThreadPool.submit(() -> {
                List<Exception> exceptions = new ArrayList<>();
                try {
                    recording.setNote(newNote.get());
                    recorder.setNote(recording.getDelegate(), newNote.get());
                } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                    exceptions.add(e);
                } finally {
                    Platform.runLater(() -> {
                        table.setCursor(Cursor.DEFAULT);
                        if (!exceptions.isEmpty()) {
                            showErrorDialog("Error while saving note", "", exceptions);
                        }
                    });
                }
            });
        }
    }

    private void pin(List<JavaFxRecording> recordings) {
        table.setCursor(Cursor.WAIT);
        GlobalThreadPool.submit(() -> {
            List<Exception> exceptions = new ArrayList<>();
            try {
                for (JavaFxRecording javaFxRecording : recordings) {
                    var rec = javaFxRecording.getDelegate();
                    try {
                        recorder.pin(rec);
                        javaFxRecording.setPinned(true);
                    } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                        exceptions.add(e);
                    }
                }
            } finally {
                Platform.runLater(() -> {
                    table.setCursor(Cursor.DEFAULT);
                    if (!exceptions.isEmpty()) {
                        showErrorDialog("Error while pinning recordings", "At least one recording couldn't be pinned", exceptions);
                    }
                });
            }
        });
    }

    private void unpin(List<JavaFxRecording> recordings) {
        table.setCursor(Cursor.WAIT);
        GlobalThreadPool.submit(() -> {
            List<Exception> exceptions = new ArrayList<>();
            try {
                for (JavaFxRecording javaFxRecording : recordings) {
                    var rec = javaFxRecording.getDelegate();
                    try {
                        recorder.unpin(rec);
                        javaFxRecording.setPinned(false);
                    } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                        exceptions.add(e);
                    }
                }
            } finally {
                Platform.runLater(() -> {
                    table.setCursor(Cursor.DEFAULT);
                    if (!exceptions.isEmpty()) {
                        showErrorDialog("Error while unpinning recordings", "At least one recording couldn't be unpinned", exceptions);
                    }
                });
            }
        });
    }

    private void jumpToNextModel(KeyCode code) {
        try {
            ensureTableIsNotEmpty();
            ensureKeyCodeIsLetterOrDigit(code);

            // determine where to start looking for the next model
            var startAt = 0;
            if (table.getSelectionModel().getSelectedIndex() >= 0) {
                startAt = table.getSelectionModel().getSelectedIndex() + 1;
                if (startAt >= table.getItems().size()) {
                    startAt = 0;
                }
            }

            String c = code.getChar().toLowerCase();
            int i = startAt;
            do {
                JavaFxRecording current = table.getItems().get(i);
                if (current.getModel().getName().toLowerCase().replaceAll("[^0-9a-z]", "").startsWith(c)) {
                    table.getSelectionModel().clearAndSelect(i);
                    table.scrollTo(i);
                    break;
                }

                i++;
                if (i >= table.getItems().size()) {
                    i = 0;
                }
            } while (i != startAt);
        } catch (IllegalStateException | IllegalArgumentException e) {
            // GUI was not in the state to process the user input
        }
    }

    private void ensureKeyCodeIsLetterOrDigit(KeyCode code) {
        if (!(code.isLetterKey() || code.isDigitKey())) {
            throw new IllegalArgumentException("keycode not allowed");
        }
    }

    private void ensureTableIsNotEmpty() {
        if (table.getItems().isEmpty()) {
            throw new IllegalStateException("table is empty");
        }
    }

    private void onOpenDirectory(JavaFxRecording first) {
        var tsFile = first.getAbsoluteFile();
        GlobalThreadPool.submit(() -> DesktopIntegration.open(tsFile.getParent()));
    }

    private void triggerPostProcessing(List<JavaFxRecording> recs) {
        GlobalThreadPool.submit(() -> {
            for (JavaFxRecording rec : recs) {
                try {
                    recorder.rerunPostProcessing(rec.getDelegate());
                } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e1) {
                    showErrorDialog("Error while starting post-processing", "The post-processing could not be started", e1);
                    LOG.error("Error while starting post-processing", e1);
                }
            }
        });
    }

    private void download(Recording recording) {
        try {
            LOG.debug("Path {}", recording.getAbsoluteFile());
            String filename = proposeTargetFilename(recording);
            var chooser = new FileChooser();
            chooser.setInitialFileName(filename);
            if (config.getSettings().lastDownloadDir != null && !config.getSettings().lastDownloadDir.equals("")) {
                var dir = new File(config.getSettings().lastDownloadDir);
                while (!dir.exists()) {
                    dir = dir.getParentFile();
                }
                chooser.setInitialDirectory(dir);
            }
            File target = chooser.showSaveDialog(null);
            if (target != null) {
                config.getSettings().lastDownloadDir = target.getParent();
                startDownloadThread(target, recording);
                recording.setStatus(DOWNLOADING);
                recording.setProgress(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String proposeTargetFilename(Recording recording) {
        if (recording.isSingleFile()) {
            return recording.getPostProcessedFile().getName();
        } else {
            String downloadFilename = config.getSettings().downloadFilename;
            String fileSuffix = config.getSettings().ffmpegFileSuffix;
            var ctx = new PostProcessingContext();
            ctx.setRecording(recording);
            ctx.setConfig(config);
            ctx.setRecorder(recorder);
            return new DownloadPostprocessor().fillInPlaceHolders(downloadFilename, ctx) + '.' + fileSuffix;
        }
    }

    private void startDownloadThread(File target, Recording recording) {
        var t = new Thread(() -> {
            try {
                String hlsBase = config.getServerUrl() + "/hls";
                if (recording.isSingleFile()) {
                    var download = new FileDownload(CamrecApplication.httpClient, createDownloadListener(recording));
                    download.start(new URL(hlsBase + '/' + recording.getId()), target);
                } else {
                    var url = new URL(hlsBase + '/' + recording.getId() + "/playlist.m3u8");
                    var download = new RecordingDownload(CamrecApplication.httpClient);
                    download.init(config, recording.getModel(), Instant.now(), Executors.newSingleThreadExecutor());
                    LOG.info("Downloading {}", url);
                    download.downloadFinishedRecording(url.toString(), target, createDownloadListener(recording), recording.getSizeInByte());
                }
            } catch (FileNotFoundException e) {
                showErrorDialog(ERROR_WHILE_DOWNLOADING_RECORDING, "The target file couldn't be created", e);
                LOG.error(ERROR_WHILE_DOWNLOADING_RECORDING, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                showErrorDialog(ERROR_WHILE_DOWNLOADING_RECORDING, "The recording could not be downloaded", e);
                LOG.error(ERROR_WHILE_DOWNLOADING_RECORDING, e);
            } catch (Exception e) {
                showErrorDialog(ERROR_WHILE_DOWNLOADING_RECORDING, "The recording could not be downloaded", e);
                LOG.error(ERROR_WHILE_DOWNLOADING_RECORDING, e);
            } finally {
                Platform.runLater(() -> {
                    recording.setStatus(FINISHED);
                    recording.setProgress(-1);
                    var evt = new RecordingStateChangedEvent(target, recording.getStatus(), recording.getModel(), recording.getStartDate());
                    EventBusHolder.BUS.post(evt);
                });
            }
        });
        t.setDaemon(true);
        t.setName("Download Thread " + recording.getAbsoluteFile().toString());
        t.start();
    }

    private ProgressListener createDownloadListener(Recording recording) {
        return progress -> Platform.runLater(() -> {
            if (progress == 100) {
                recording.setStatus(FINISHED);
                recording.setProgress(-1);
                LOG.debug("Download finished for recording {} - {}", recording.getId(), recording.getAbsoluteFile());
            } else {
                recording.setStatus(DOWNLOADING);
                recording.setProgress(progress);
            }
        });
    }

    private void showErrorDialog(final String title, final String msg, final Exception e) {
        showErrorDialog(title, msg, Collections.singletonList(e));
    }

    private void showErrorDialog(final String title, final String msg, final List<Exception> exceptions) {
        Platform.runLater(() -> {
            var autosizeAlert = new AutosizeAlert(AlertType.ERROR, getTabPane().getScene());
            autosizeAlert.setTitle(title);
            autosizeAlert.setHeaderText(msg);
            var contentText = new StringBuilder("On or more error(s) occurred:");
            for (Exception exception : exceptions) {
                contentText.append("\n• ").append(exception.getLocalizedMessage());
            }
            autosizeAlert.setContentText(contentText.toString());
            autosizeAlert.showAndWait();
        });
    }

    private void play(Recording recording) {
        GlobalThreadPool.submit(() -> {
            boolean started = Player.play(recording);
            if (started && Config.getInstance().getSettings().showPlayerStarting) {
                Platform.runLater(() -> Toast.makeText(getTabPane().getScene(), "Starting Player", 2000, 500, 500));
            }
        });
    }

    private void play(Model model) {
        new PlayAction(table, model).execute();
    }

    private void delete(List<JavaFxRecording> recordings) {
        table.setCursor(Cursor.WAIT);
        String msg;
        if (recordings.size() > 1) {
            msg = "Delete " + recordings.size() + " recordings for good?";
        } else {
            Recording r = recordings.get(0);
            msg = "Delete " + r.getModel().getName() + "/" + r.getStartDate() + " for good?";
        }
        var confirm = new AutosizeAlert(AlertType.CONFIRMATION, msg, getTabPane().getScene(), YES, NO);
        confirm.setTitle("Delete recording?");
        confirm.setHeaderText(msg);
        confirm.setContentText("");
        confirm.showAndWait();
        if (confirm.getResult() == ButtonType.YES) {
            deleteAsync(recordings);
        } else {
            table.setCursor(Cursor.DEFAULT);
        }
    }

    private void deleteAsync(List<JavaFxRecording> recordings) {
        GlobalThreadPool.submit(() -> {
            recordingsLock.lock();
            try {
                List<Recording> deleted = new ArrayList<>();
                List<Exception> exceptions = new ArrayList<>();
                for (JavaFxRecording r : recordings) {
                    if (r.getStatus() != FINISHED && r.getStatus() != FAILED && r.getStatus() != WAITING) {
                        continue;
                    }
                    try {
                        recorder.delete(r.getDelegate());
                        deleted.add(r);
                    } catch (Exception e1) {
                        exceptions.add(e1);
                        LOG.error("Error while deleting recording", e1);
                    }
                }
                if (!exceptions.isEmpty()) {
                    showErrorDialog("Error while deleting recording", "Recording not deleted", exceptions);
                }
                observableRecordings.removeAll(deleted);
            } finally {
                recordingsLock.unlock();
                Platform.runLater(() -> table.setCursor(Cursor.DEFAULT));
            }
        });
    }

    @Override
    public void onShutdown() {
        table.saveState();
    }
}
