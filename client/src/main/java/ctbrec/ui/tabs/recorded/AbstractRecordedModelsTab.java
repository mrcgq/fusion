package ctbrec.ui.tabs.recorded;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.eventbus.Subscribe;
import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.event.EventBusHolder;
import ctbrec.image.PortraitStore;
import ctbrec.io.HttpException;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.AutosizeAlert;
import ctbrec.ui.CamrecApplication;
import ctbrec.ui.JavaFxModel;
import ctbrec.ui.PreviewPopupHandler;
import ctbrec.ui.action.AbstractPortraitAction.PortraitChangedEvent;
import ctbrec.ui.action.MarkForLaterRecordingAction;
import ctbrec.ui.action.PlayAction;
import ctbrec.ui.action.StartRecordingAction;
import ctbrec.ui.controls.CustomMouseBehaviorContextMenu;
import ctbrec.ui.controls.DateTimeCellFactory;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.SearchBox;
import ctbrec.ui.controls.autocomplete.AutoFillTextField;
import ctbrec.ui.controls.autocomplete.ObservableListSuggester;
import ctbrec.ui.controls.table.SettingTableViewStateStore;
import ctbrec.ui.controls.table.StatePersistingTableView;
import ctbrec.ui.menu.ModelMenuContributor;
import ctbrec.ui.tabs.TabSelectionListener;
import ctbrec.ui.tabs.recorded.ModelImportExport.ExportOptions;
import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringPropertyBase;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public abstract class AbstractRecordedModelsTab extends Tab implements TabSelectionListener {
    private static final Image SILHOUETTE = new Image(AbstractRecordedModelsTab.class.getResourceAsStream("/silhouette_256.png"));
    protected static final String STYLE_ALIGN_CENTER = "-fx-alignment: CENTER;";

    protected ReentrantLock lock = new ReentrantLock();
    protected ObservableList<JavaFxModel> observableModels = FXCollections.observableArrayList();
    protected ObservableList<JavaFxModel> filteredModels = FXCollections.observableArrayList();

    protected SettingTableViewStateStore tableStateStore;
    protected StatePersistingTableView<JavaFxModel> table;
    protected List<TableColumn<JavaFxModel, ?>> columns = new ArrayList<>();
    protected LoadingCache<Model, Image> portraitCache = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build(CacheLoader.from(this::loadModelPortrait));

    protected AutoFillTextField modelInputField;
    protected List<Site> sites;
    protected Recorder recorder;

    protected HBox addModelBox = new HBox(5);
    protected HBox filterContainer = new HBox(5);
    protected Label modelLabel = new Label("Model");
    protected Button addModelButton = new Button("Record");
    protected Button checkModelAccountExistance = new Button("Check URLs");
    protected Button exportModelsButton = new Button();
    protected Button importModelsButton = new Button();
    protected TextField filterTextField;

    protected FlowPane grid = new FlowPane();
    protected ScrollPane scrollPane = new ScrollPane();
    protected ContextMenu popup;

    protected Config config;
    protected PortraitStore portraitStore;

    AbstractRecordedModelsTab(String text, String stateStorePrefix) {
        super(text);
        config = Config.getInstance();
        portraitStore = CamrecApplication.portraitStore;
        tableStateStore = new SettingTableViewStateStore(config, stateStorePrefix);
        table = new StatePersistingTableView<>(tableStateStore);
        registerPortraitListener();
    }

    protected void registerPortraitListener() {
        EventBusHolder.BUS.register(this);
    }

    @Subscribe
    public void portraitChanged(PortraitChangedEvent e) {
        log.debug("Invalidate cache for {}", e.getModel());
        portraitCache.invalidate(e.getModel());
        if (table != null) {
            table.refresh();
        }
    }

    protected void createGui() {
        grid.setPadding(new Insets(5));
        grid.setHgap(5);
        grid.setVgap(5);

        scrollPane.setContent(grid);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        BorderPane.setMargin(scrollPane, new Insets(5));

        table.setEditable(true);
        if (!config.getSettings().showGridLinesInTables) {
            table.setStyle("-fx-table-cell-border-color: transparent;");
        }
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        var previewPopupHandler = new PreviewPopupHandler(table);
        table.setRowFactory(tableview -> {
            TableRow<JavaFxModel> row = new TableRow<>();
            row.addEventHandler(MouseEvent.ANY, previewPopupHandler);
            return row;
        });
        table.setItems(observableModels);
        table.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> {
            popup = createContextMenu();
            if (popup != null) {
                popup.show(table, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
        table.addEventHandler(MouseEvent.MOUSE_PRESSED, event -> {
            if (popup != null) {
                popup.hide();
            }
        });
        table.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            List<JavaFxModel> selectedModels = table.getSelectionModel().getSelectedItems();
            if (event.getCode() == KeyCode.DELETE) {
                stopAction(selectedModels);
            } else {
                jumpToNextModel(event.getCode());
            }
        });

        scrollPane.setContent(table);

        checkModelAccountExistance.setPadding(new Insets(5));
        checkModelAccountExistance.setTooltip(new Tooltip("Go over all model URLs and check, if the account still exists"));
        HBox.setMargin(checkModelAccountExistance, new Insets(0, 0, 0, 20));

        modelLabel.setPadding(new Insets(5, 0, 0, 0));
        ObservableList<String> suggestions = FXCollections.observableArrayList();
        sites.forEach(site -> suggestions.add(site.getClass().getSimpleName()));
        modelInputField = new AutoFillTextField(new ObservableListSuggester(suggestions));
        modelInputField.minWidth(150);
        modelInputField.prefWidth(600);
        HBox.setHgrow(modelInputField, Priority.ALWAYS);
        modelInputField.setPromptText("e.g. MyFreeCams:ModelName or an URL like https://chaturbate.com/modelname/");
        modelInputField.onActionHandler(this::addModel);
        modelInputField.setTooltip(new Tooltip("To add a model enter SiteName:ModelName\n" + "press ENTER to confirm a suggested site name"));
        BorderPane.setMargin(addModelBox, new Insets(5));
        addModelButton.setOnAction(this::addModel);
        addModelButton.setPadding(new Insets(5));
        ImageView exportIcon = new ImageView(Objects.requireNonNull(getClass().getResource("/16/download.png"), "/16/download.png not found").toString());
        exportModelsButton.setGraphic(exportIcon);
        exportModelsButton.setTooltip(new Tooltip("Export models to file"));
        exportModelsButton.setMinWidth(34);
        exportModelsButton.setMinHeight(26);
        exportModelsButton.setOnAction(this::exportModels);
        ImageView importIcon = new ImageView(Objects.requireNonNull(getClass().getResource("/16/upload.png"), "/16/upload.png not found").toString());
        importModelsButton.setGraphic(importIcon);
        importModelsButton.setTooltip(new Tooltip("Import models from file"));
        importModelsButton.setMinWidth(34);
        importModelsButton.setMinHeight(26);
        importModelsButton.setOnAction(this::importModels);
        HBox.setMargin(exportModelsButton, new Insets(0, 0, 0, 20));
        addModelBox.getChildren().addAll(modelLabel, modelInputField, addModelButton, checkModelAccountExistance, exportModelsButton, importModelsButton);

        filterContainer.setPadding(new Insets(0));
        filterContainer.setAlignment(Pos.CENTER_RIGHT);
        filterContainer.minWidth(100);
        filterContainer.prefWidth(150);
        HBox.setHgrow(filterContainer, Priority.ALWAYS);
        filterTextField = new SearchBox(false);
        filterTextField.minWidth(100);
        filterTextField.prefWidth(150);
        filterTextField.setPromptText("Filter");
        filterTextField.textProperty().addListener((observableValue, oldValue, newValue) -> {
            String q = filterTextField.getText();
            lock.lock();
            try {
                filter(q);
            } finally {
                lock.unlock();
            }
        });
        filterTextField.getStyleClass().remove("search-box-icon");

        filterContainer.getChildren().addAll(filterTextField);
        addModelBox.getChildren().add(filterContainer);
    }

    protected abstract List<Model> getExportList();

    private void exportModels(ActionEvent actionEvent) {
        ExportOptions exportOptions = new ModelExportDialog(getTabPane()).showAndWait();
        if (exportOptions != null) {
            try {
                var groups = recorder.getModelGroups();
                ModelImportExport.exportTo(getExportList(), groups, config, exportOptions);
            } catch (IOException e) {
                String msg = "An error occurred while exporting the model list";
                Dialogs.showError(getTabPane().getScene(), "Export models", msg, e);
                log.error(msg, e);
            }
        }
    }

    protected void importModelList(List<Model> models) {
        getContent().setCursor(Cursor.WAIT);
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() {
                for (Model model : models) {
                    try {
                        Instant addedTimestamp = model.getAddedTimestamp(); // preserve old addedTimestamp
                        recorder.addModel(model);
                        model.setAddedTimestamp(addedTimestamp); // restore old addedTimestamp
                    } catch (Exception e) {
                        log.error("Couldn't add model to recording list", e);
                    }
                }
                return null;
            }

            @Override
            protected void done() {
                getContent().setCursor(Cursor.DEFAULT);
                reload();
            }
        };
        GlobalThreadPool.submit(task);
    }

    private void importModels(ActionEvent actionEvent) {
        var chooser = new FileChooser();
        File target = chooser.showOpenDialog(getTabPane().getScene().getWindow());
        if (target != null) {
            try {
                List<Model> models = ModelImportExport.importFrom(target, sites, config);
                importModelList(models);
                portraitCache.invalidateAll();
            } catch (IOException e) {
                String msg = "An error occurred while importing the model list";
                Dialogs.showError(getTabPane().getScene(), "Import models", msg, e);
                log.error(msg, e);
            }
        }
    }

    protected void addPreviewColumn(int columnIdx) {
        TableColumn<JavaFxModel, String> preview = addTableColumn("preview", "🎥", columnIdx, 35);
        preview.setCellValueFactory(cdf -> new SimpleStringProperty("  ▶  "));
        preview.setEditable(false);
    }

    protected void addPortraitColumn(int columnIdx) {
        TableColumn<JavaFxModel, Image> portrait = addTableColumn("portrait", "Portrait", columnIdx, 80);
        portrait.setCellValueFactory(param -> {
            Model mdl = param.getValue().getDelegate();
            SimpleObjectProperty<Image> imgProperty = new SimpleObjectProperty<>();
            Image image = portraitCache.getIfPresent(mdl);
            if (image == null && portrait.isVisible()) {
                GlobalThreadPool.submit(() -> portraitCache.put(mdl, loadModelPortrait(mdl)));
            }
            if (Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
                image = SILHOUETTE;
            }
            imgProperty.set(image);
            return imgProperty;
        });
        portrait.setCellFactory(param -> new ImageTableCell());
        portrait.setEditable(false);
    }

    protected void addModelColumn(int columnIdx) {
        TableColumn<JavaFxModel, ModelName> name = addTableColumn("name", "Model", columnIdx, 200);
        name.setCellValueFactory(param -> {
            var modelName = new ModelName(param.getValue(), recorder);
            return new SimpleObjectProperty<>(modelName);
        });
        name.setCellFactory(param -> new ModelNameTableCell(recorder));
        name.setEditable(false);
    }

    protected void addUrlColumn(int columnIdx) {
        TableColumn<JavaFxModel, String> url = addTableColumn("url", "URL", columnIdx, 400);
        url.setCellValueFactory(new PropertyValueFactory<>("url"));
        url.setCellFactory(new ClickableCellFactory<>());
        url.setEditable(false);
    }

    protected void addAddedTimestampColumn(int columnIdx) {
        TableColumn<JavaFxModel, Instant> tc = addTableColumn("addedTimestamp", "Added at", columnIdx, 400);
        tc.setCellFactory(new DateTimeCellFactory<>(config.getDateTimeFormatter()));
        tc.setCellValueFactory(param -> new SimpleObjectProperty<>(param.getValue().getAddedTimestamp()));
        tc.setPrefWidth(150);
        tc.setEditable(false);
        tc.setStyle(STYLE_ALIGN_CENTER);
    }

    protected void addNotesColumn(int columnIdx) {
        TableColumn<JavaFxModel, String> notes = addTableColumn("notes", "Notes", columnIdx, 400);
        notes.setCellValueFactory(cdf -> {
            JavaFxModel m = cdf.getValue();
            return new StringPropertyBase() {
                @Override
                public String getName() {
                    return "Model Notes";
                }

                @Override
                public Object getBean() {
                    return null;
                }

                @Override
                public String get() {
                    String modelNotes;
                    try {
                        modelNotes = CamrecApplication.modelNotesService.loadModelNotes(m.getUrl()).orElse("");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                    return modelNotes;
                }
            };
        });
        notes.setEditable(false);
    }

    abstract void stopAction(List<JavaFxModel> selectedModels);

    protected <T> TableColumn<JavaFxModel, T> addTableColumn(String id, String text, int index, int width) {
        TableColumn<JavaFxModel, T> tc = new TableColumn<>(text);
        tc.setId(id);
        tc.setText(text);
        tc.setUserData(index);
        tc.setPrefWidth(width);
        tc.setStyle("-fx-alignment: CENTER-LEFT;");
        columns.add(tc);
        table.getColumns().add(tc);
        return tc;
    }

    protected ContextMenu createContextMenu() {
        List<Model> selectedModels = table.getSelectionModel().getSelectedItems().stream().map(JavaFxModel::getDelegate).toList();
        if (selectedModels.isEmpty()) {
            return null;
        }

        ContextMenu menu = new CustomMouseBehaviorContextMenu();

        ModelMenuContributor.newContributor(getTabPane(), Config.getInstance(), recorder) //
                .withStartStopCallback(m -> Platform.runLater(this::reload)) //
                .removeModelAfterIgnore(true) //
                //                .withPortraitCallback(m -> Platform.runLater(() -> {
                //                    portraitCache.invalidate(m);
                //                    table.refresh();
                //                }))
                .afterwards(() -> Platform.runLater(this::reload))
                .contributeToMenu(selectedModels, menu);

        return menu;
    }

    protected void reload() {
        deselected();
        selected();
    }

    protected void addModel(ActionEvent e) {
        String input = modelInputField.getText().trim();
        if (StringUtil.isBlank(input)) {
            return;
        }

        if (input.startsWith("http")) {
            addModelByUrl(input);
        } else {
            addModelByName(input);
        }
    }

    protected void addModelByUrl(String url) {
        for (Site site : sites) {
            var newModel = site.createModelFromUrl(url);
            if (newModel != null) {
                if (getMarkModelsForLaterRecording()) {
                    new MarkForLaterRecordingAction(modelInputField, List.of(newModel), true, recorder).execute(m -> Platform.runLater(this::reload));
                } else {
                    new StartRecordingAction(modelInputField, List.of(newModel), recorder)
                            .execute()
                            .whenComplete((r, ex) -> Platform.runLater(this::reload));
                }
                return;
            }
        }

        Dialogs.showError(getTabPane().getScene(), "Unknown URL format",
                "The URL you entered has an unknown format or the function does not support this site, yet", null);
    }

    abstract boolean getMarkModelsForLaterRecording();

    protected void addModelByName(String siteModelCombo) {
        String[] parts = siteModelCombo.trim().split(":");
        if (parts.length != 2) {
            Dialogs.showError(getTabPane().getScene(), "Wrong input format", "Use something like \"MyFreeCams:ModelName\"", null);
            return;
        }

        String siteName = parts[0];
        String modelName = parts[1];
        for (Site site : sites) {
            if (Objects.equals(siteName.toLowerCase(), site.getClass().getSimpleName().toLowerCase())) {
                var newModel = site.createModel(modelName);
                if (getMarkModelsForLaterRecording()) {
                    new MarkForLaterRecordingAction(modelInputField, List.of(newModel), true, recorder).execute(m -> Platform.runLater(this::reload));
                } else {
                    new StartRecordingAction(modelInputField, List.of(newModel), recorder)
                            .execute()
                            .whenComplete((r, ex) -> Platform.runLater(this::reload));
                }
                return;
            }
        }

        Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, getTabPane().getScene());
        alert.setTitle("Unknown site");
        alert.setHeaderText("Couldn't add model");
        alert.setContentText("The site you entered is unknown");
        alert.showAndWait();
    }

    protected void jumpToNextModel(KeyCode code) {
        if (!table.getItems().isEmpty() && (code.isLetterKey() || code.isDigitKey())) {
            // determine where to start looking for the next model
            var startAt = getJumpToStartIndex();

            String c = code.getChar().toLowerCase();
            int i = startAt;
            do {
                JavaFxModel current = table.getItems().get(i);
                if (current.getName().toLowerCase().replaceAll("[^0-9a-z]", "").startsWith(c)) {
                    table.getSelectionModel().clearAndSelect(i);
                    table.scrollTo(i);
                    break;
                }

                i++;
                if (i >= table.getItems().size()) {
                    i = 0;
                }
            } while (i != startAt);
        }
    }

    protected int getJumpToStartIndex() {
        var startAt = 0;
        if (table.getSelectionModel().getSelectedIndex() >= 0) {
            startAt = table.getSelectionModel().getSelectedIndex() + 1;
            if (startAt >= table.getItems().size()) {
                startAt = 0;
            }
        }
        return startAt;
    }

    protected void filter(String filter) {
        lock.lock();
        try {
            if (StringUtil.isBlank(filter)) {
                observableModels.addAll(filteredModels);
                filteredModels.clear();
                return;
            }

            String[] tokens = filter.split(" ");
            observableModels.addAll(filteredModels);
            filteredModels.clear();
            for (var i = 0; i < table.getItems().size(); i++) {
                var sb = new StringBuilder();
                for (TableColumn<JavaFxModel, ?> tc : table.getColumns()) {
                    Object cellData = tc.getCellData(i);
                    if (cellData != null) {
                        var content = cellData.toString();
                        sb.append(content).append(' ');
                    }
                }
                var searchText = sb.toString();

                var tokensMissing = false;
                for (String token : tokens) {
                    if (!searchText.toLowerCase().contains(token.toLowerCase())) {
                        tokensMissing = true;
                        break;
                    }
                }
                if (tokensMissing) {
                    JavaFxModel filteredModel = table.getItems().get(i);
                    filteredModels.add(filteredModel);
                }
            }
            observableModels.removeAll(filteredModels);
        } finally {
            lock.unlock();
        }
    }

    public void saveState() {
        table.saveState();
    }

    protected void restoreState() {
        table.restoreState();
        table.getColumns().stream().filter(tc -> Objects.equals(tc.getId(), "preview")).findFirst().ifPresent(tc -> tc.setVisible(config.getSettings().livePreviews));
    }

    protected class ClickableCellFactory<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {
        @Override
        public TableCell<S, T> call(TableColumn<S, T> param) {
            TableCell<S, T> cell = new TableCell<>() {
                @Override
                protected void updateItem(Object item, boolean empty) {
                    setText(empty ? "" : Objects.toString(item));
                }
            };

            cell.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
                if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    JavaFxModel selectedModel = table.getSelectionModel().getSelectedItem();
                    if (selectedModel != null) {
                        new PlayAction(table, selectedModel).execute();
                    }
                }
            });
            return cell;
        }
    }

    protected Image loadModelPortrait(Model model) {
        try {
            return portraitStore
                    .loadModelPortraitByModelUrl(model.getUrl())
                    .map(bytes -> new Image(new ByteArrayInputStream(bytes)))
                    .orElse(SILHOUETTE);
        } catch (HttpException e) {
            if (e.getResponseCode() != 404) {
                log.debug("Could not load portrait from server", e);
            }
        } catch (IOException e) {
            log.debug("Could not load portrait from server", e);
        }
        return SILHOUETTE;
    }
}
