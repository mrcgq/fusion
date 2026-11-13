package ctbrec.ui.sites.myfreecams;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.sites.mfc.MyFreeCamsModel;
import ctbrec.sites.mfc.SessionState;
import ctbrec.sites.mfc.User;
import ctbrec.ui.controls.CustomMouseBehaviorContextMenu;
import ctbrec.ui.controls.SearchBox;
import ctbrec.ui.menu.ModelMenuContributor;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Worker.State;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;

@Slf4j
public class MyFreeCamsTableTab extends Tab implements TabSelectionListener {
    private final ScrollPane scrollPane = new ScrollPane();
    private final TableView<ModelTableRow> table = new TableView<>();
    private final ObservableList<ModelTableRow> filteredModels = FXCollections.observableArrayList();
    private final ObservableList<ModelTableRow> observableModels = FXCollections.observableArrayList();
    private final Recorder recorder;
    private final MyFreeCams mfc;
    private final ReentrantLock lock = new ReentrantLock();
    private final Label count = new Label("models");
    private final List<TableColumn<ModelTableRow, ?>> columns = new ArrayList<>();
    private TableUpdateService updateService;
    private SearchBox filterInput;
    private ContextMenu popup;
    private long lastJsonWrite = 0;

    public MyFreeCamsTableTab(MyFreeCams mfc, Recorder recorder) {
        this.mfc = mfc;
        this.recorder = recorder;
        setText("Tabular");
        setClosable(false);
        createGui();
        restoreState();
        loadData();
        initUpdateService();
        filter(filterInput.getText());
    }

    private void initUpdateService() {
        updateService = new TableUpdateService(mfc);
        updateService.setPeriod(new Duration(TimeUnit.SECONDS.toMillis(1)));
        updateService.setOnSucceeded(this::onSuccess);
        updateService.setOnFailed(event -> log.info("Couldn't update MyFreeCams model table", event.getSource().getException()));
    }

    private void onSuccess(WorkerStateEvent evt) {
        Collection<SessionState> sessionStates = updateService.getValue();
        if (sessionStates == null) {
            return;
        }

        lock.lock();
        try {
            for (SessionState updatedModel : sessionStates) {
                var row = new ModelTableRow(updatedModel);
                int index = observableModels.indexOf(row);
                if (index == -1) {
                    observableModels.add(row);
                } else {
                    observableModels.get(index).update(updatedModel);
                }
            }

            for (Iterator<ModelTableRow> iterator = observableModels.iterator(); iterator.hasNext(); ) {
                ModelTableRow model = iterator.next();
                var found = false;
                for (SessionState sessionState : sessionStates) {
                    if (Objects.equals(sessionState.getUid(), model.uid)) {
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    iterator.remove();
                }
            }
        } finally {
            lock.unlock();
        }

        filteredModels.clear();
        filter(filterInput.getText());
        table.sort();

        long now = System.currentTimeMillis();
        if ((now - lastJsonWrite) > TimeUnit.SECONDS.toMillis(30)) {
            lastJsonWrite = now;
            saveData();
        }
    }

    private void createGui() {
        Config config = Config.getInstance();
        var layout = new BorderPane();
        layout.setPadding(new Insets(5, 10, 10, 10));

        filterInput = new SearchBox(false);
        filterInput.setPromptText("Filter");
        filterInput.textProperty().addListener((observableValue, oldValue, newValue) -> {
            String filter = filterInput.getText();
            Config.getInstance().getSettings().mfcModelsTableFilter = filter;
            lock.lock();
            try {
                filter(filter);
            } finally {
                lock.unlock();
            }
        });
        filterInput.getStyleClass().remove("search-box-icon");
        HBox.setHgrow(filterInput, Priority.ALWAYS);
        var export = new Button("⬇");
        export.setOnAction(this::export);
        export.setTooltip(new Tooltip("Export data"));

        var columnSelection = new Button("⚙");
        columnSelection.setOnAction(this::showColumnSelection);
        columnSelection.setTooltip(new Tooltip("Select columns"));
        var topBar = new HBox(5);
        topBar.getChildren().addAll(filterInput, count, export, columnSelection);
        count.prefHeightProperty().bind(filterInput.heightProperty());
        count.setAlignment(Pos.CENTER);
        layout.setTop(topBar);
        BorderPane.setMargin(topBar, new Insets(0, 0, 5, 0));

        table.setItems(observableModels);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getSortOrder().addListener(createSortOrderChangedListener());
        if (!config.getSettings().showGridLinesInTables) {
            table.setStyle("-fx-table-cell-border-color: transparent;");
        }
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
        table.getColumns().addListener((ListChangeListener<TableColumn<?, ?>>) (e -> saveState()));

        var idx = 0;
        TableColumn<ModelTableRow, Number> uid = createTableColumn("UID", 65, idx++);
        uid.setId("uid");
        uid.setCellValueFactory(cdf -> cdf.getValue().uidProperty());
        addTableColumnIfEnabled(uid);

        TableColumn<ModelTableRow, String> name = createTableColumn("Name", 200, idx++);
        name.setId("name");
        name.setCellValueFactory(cdf -> cdf.getValue().nameProperty());
        addTableColumnIfEnabled(name);

        TableColumn<ModelTableRow, String> state = createTableColumn("State", 130, idx++);
        state.setId("state");
        state.setCellValueFactory(cdf -> cdf.getValue().stateProperty());
        addTableColumnIfEnabled(state);

        TableColumn<ModelTableRow, Boolean> hd = createTableColumn("HD", 130, idx++);
        hd.setId("hd");
        hd.setCellValueFactory(cdf -> cdf.getValue().hdProperty());
        addTableColumnIfEnabled(hd);

        TableColumn<ModelTableRow, Boolean> webrtc = createTableColumn("webrtc", 130, idx++);
        webrtc.setId("webrtc");
        webrtc.setCellValueFactory(cdf -> cdf.getValue().webrtcProperty());
        addTableColumnIfEnabled(webrtc);

        TableColumn<ModelTableRow, Number> flags = createTableColumn("Flags", 75, idx++);
        flags.setId("flags");
        flags.setCellValueFactory(cdf -> cdf.getValue().flagsProperty());
        addTableColumnIfEnabled(flags);

        TableColumn<ModelTableRow, Number> camscore = createTableColumn("Score", 75, idx++);
        camscore.setId("camscore");
        camscore.setCellValueFactory(cdf -> cdf.getValue().camScoreProperty());
        addTableColumnIfEnabled(camscore);

        TableColumn<ModelTableRow, String> newModel = createTableColumn("New", 60, idx++);
        newModel.setId("newModel");
        newModel.setCellValueFactory(cdf -> cdf.getValue().newModelProperty());
        addTableColumnIfEnabled(newModel);

        TableColumn<ModelTableRow, String> ethnic = createTableColumn("Ethnicity", 130, idx++);
        ethnic.setId("ethnic");
        ethnic.setCellValueFactory(cdf -> cdf.getValue().ethnicityProperty());
        addTableColumnIfEnabled(ethnic);

        TableColumn<ModelTableRow, String> country = createTableColumn("Country", 160, idx++);
        country.setId("country");
        country.setCellValueFactory(cdf -> cdf.getValue().countryProperty());
        addTableColumnIfEnabled(country);

        TableColumn<ModelTableRow, String> continent = createTableColumn("Continent", 100, idx++);
        continent.setId("continent");
        continent.setCellValueFactory(cdf -> cdf.getValue().continentProperty());
        addTableColumnIfEnabled(continent);

        TableColumn<ModelTableRow, String> occupation = createTableColumn("Occupation", 160, idx++);
        occupation.setId("occupation");
        occupation.setCellValueFactory(cdf -> cdf.getValue().occupationProperty());
        addTableColumnIfEnabled(occupation);

        TableColumn<ModelTableRow, String> tags = createTableColumn("Tags", 300, idx++);
        tags.setId("tags");
        tags.setCellValueFactory(cdf -> cdf.getValue().tagsProperty());
        addTableColumnIfEnabled(tags);

        TableColumn<ModelTableRow, String> blurp = createTableColumn("Blurp", 300, idx++);
        blurp.setId("blurp");
        blurp.setCellValueFactory(cdf -> cdf.getValue().blurpProperty());
        addTableColumnIfEnabled(blurp);

        TableColumn<ModelTableRow, String> topic = createTableColumn("Topic", 600, idx);
        topic.setId("topic");
        topic.setCellValueFactory(cdf -> cdf.getValue().topicProperty());
        addTableColumnIfEnabled(topic);

        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(table);
        scrollPane.setStyle("-fx-background-color: -fx-background");
        layout.setCenter(scrollPane);
        setContent(layout);
    }

    private ContextMenu createContextMenu() {
        List<Model> selectedModels = getSelectedModels();
        if (selectedModels.isEmpty()) {
            return null;
        }

        ContextMenu menu = new CustomMouseBehaviorContextMenu();

        ModelMenuContributor.newContributor(getTabPane(), Config.getInstance(), recorder) //
                .withStartStopCallback(m -> getTabPane().setCursor(Cursor.DEFAULT)) //
                .afterwards(table::refresh)
                .contributeToMenu(selectedModels, menu);

        addDebuggingInDevMode(menu, selectedModels);

        return menu;
    }

    private void addDebuggingInDevMode(ContextMenu menu, List<Model> selectedModels) {
        menu.getItems().add(new SeparatorMenuItem());
        if (Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
            var debug = new MenuItem("debug");
            debug.setOnAction(e -> GlobalThreadPool.submit(() -> {
                for (Model m : selectedModels) {
                    try {
                        List<StreamSource> sources = m.getStreamSources();
                        for (StreamSource src : sources) {
                            log.info("m:{} s:{} bandwidth:{} height:{}", m.getName(), src.getMediaPlaylistUrl(), src.getBandwidth(), src.getHeight());
                        }
                        log.info("===============");
                    } catch (IOException | ExecutionException | ParseException | PlaylistException | JAXBException e1) {
                        log.error("Couldn't get stream sources", e1);
                    }
                }
            }));
            menu.getItems().add(debug);
        }
    }

    private List<Model> getSelectedModels() {
        ObservableList<ModelTableRow> selectedStates = table.getSelectionModel().getSelectedItems();
        ArrayList<Model> selectedModels = new ArrayList<>();
        for (ModelTableRow sessionState : selectedStates) {
            if (sessionState.name.get() != null) {
                MyFreeCamsModel model = mfc.createModel(sessionState.name.get());
                mfc.getClient().update(model);
                selectedModels.add(model);
            }
        }
        return selectedModels;
    }

    private void addTableColumnIfEnabled(TableColumn<ModelTableRow, ?> tc) {
        if (isColumnEnabled(tc)) {
            table.getColumns().add(tc);
        }
    }

    private void filter(String filter) {
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
                for (TableColumn<ModelTableRow, ?> tc : table.getColumns()) {
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
                    ModelTableRow sessionState = table.getItems().get(i);
                    filteredModels.add(sessionState);
                }
            }
            observableModels.removeAll(filteredModels);
        } finally {
            lock.unlock();
            int filtered = filteredModels.size();
            int showing = observableModels.size();
            int total = showing + filtered;
            count.setText(showing + "/" + total);
        }
    }

    private void export(ActionEvent evt) {
        var chooser = new FileChooser();
        var file = chooser.showSaveDialog(getTabPane().getScene().getWindow());
        if (file != null) {
            try (var fout = new FileOutputStream(file)) {
                var ps = new PrintStream(fout);
                List<ModelTableRow> union = new ArrayList<>();
                union.addAll(filteredModels);
                union.addAll(observableModels);
                ps.println("\"uid\",\"blurp\",\"camScore\",\"continent\",\"country\",\"ethnic\",\"name\",\"new\",\"occupation\",\"state\",\"tags\",\"topic\"");
                for (ModelTableRow row : union) {
                    ps.print("\"" + row.uid + "\"");
                    ps.print(',');
                    ps.print(escape(row.blurp));
                    ps.print(',');
                    ps.print(escape(row.camScore));
                    ps.print(',');
                    ps.print(escape(row.continent));
                    ps.print(',');
                    ps.print(escape(row.country));
                    ps.print(',');
                    ps.print(escape(row.ethnic));
                    ps.print(',');
                    ps.print(escape(row.name));
                    ps.print(',');
                    ps.print(escape(row.newModel));
                    ps.print(',');
                    ps.print(escape(row.occupation));
                    ps.print(',');
                    ps.print(escape(row.state));
                    ps.print(',');
                    ps.print(escape(row.tags));
                    ps.print(',');
                    ps.print(escape(row.topic));
                    ps.println();
                }
            } catch (Exception e) {
                log.debug("Couldn't write mfc models table data", e);
            }
        }
    }

    private String escape(Property<?> prop) {
        String value = prop.getValue() != null ? prop.getValue().toString() : "";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private void showColumnSelection(ActionEvent evt) {
        ContextMenu menu = new CustomMouseBehaviorContextMenu();
        for (TableColumn<ModelTableRow, ?> tc : columns) {
            var item = new CheckMenuItem(tc.getText());
            item.setSelected(isColumnEnabled(tc));
            menu.getItems().add(item);
            item.setOnAction(e -> {
                if (item.isSelected()) {
                    Config.getInstance().getSettings().mfcDisabledModelsTableColumns.remove(tc.getText());
                    boolean added = false;
                    for (int i = table.getColumns().size() - 1; i >= 0; i--) {
                        TableColumn<ModelTableRow, ?> other = table.getColumns().get(i);
                        log.debug("Adding column {}", tc.getText());
                        int idx = (int) tc.getUserData();
                        int otherIdx = (int) other.getUserData();
                        if (otherIdx < idx) {
                            table.getColumns().add(i + 1, tc);
                            added = true;
                            break;
                        }
                    }
                    if (!added) {
                        table.getColumns().add(0, tc);
                    }
                } else {
                    Config.getInstance().getSettings().mfcDisabledModelsTableColumns.add(tc.getText());
                    table.getColumns().remove(tc);
                }
            });
        }
        Button src = (Button) evt.getSource();
        Point2D location = src.localToScreen(src.getTranslateX(), src.getTranslateY());
        menu.show(getTabPane().getScene().getWindow(), location.getX(), location.getY() + src.getHeight() + 5);
    }

    private boolean isColumnEnabled(TableColumn<ModelTableRow, ?> tc) {
        return !Config.getInstance().getSettings().mfcDisabledModelsTableColumns.contains(tc.getText());
    }

    private <T> TableColumn<ModelTableRow, T> createTableColumn(String text, int width, int idx) {
        TableColumn<ModelTableRow, T> tc = new TableColumn<>(text);
        tc.setPrefWidth(width);
        tc.sortTypeProperty().addListener((obs, o, n) -> saveState());
        tc.widthProperty().addListener((obs, o, n) -> saveState());
        tc.setUserData(idx);
        columns.add(tc);
        return tc;
    }

    @Override
    public void selected() {
        if (updateService != null) {
            var s = updateService.getState();
            if (s != State.SCHEDULED && s != State.RUNNING) {
                updateService.reset();
                updateService.restart();
            }
        }
    }

    @Override
    public void deselected() {
        if (updateService != null) {
            updateService.cancel();
        }
        saveData();
        observableModels.clear();
    }

    private void saveData() {
        try {
            List<ModelTableRow> union = new ArrayList<>();
            union.addAll(filteredModels);
            union.addAll(observableModels);
            var data = new JSONArray();
            for (ModelTableRow row : union) {
                var model = new JSONObject();
                model.put("uid", row.uid);
                model.put("blurp", row.blurp.get());
                model.put("camScore", row.camScore.get());
                model.put("continent", row.continent.get());
                model.put("country", row.country.get());
                model.put("ethnic", row.ethnic.get());
                model.put("name", row.name.get());
                model.put("newModel", row.newModel.get());
                model.put("occupation", row.occupation.get());
                model.put("state", row.state.get());
                model.put("tags", row.tags.get());
                model.put("topic", row.topic.get());
                data.put(model);
            }
            var file = new File(Config.getInstance().getConfigDir(), "mfc-models.json");
            Files.writeString(file.toPath(), data.toString(2), CREATE, WRITE);
            saveState();
        } catch (Exception e) {
            log.debug("Couldn't write mfc models table data: {}", e.getMessage());
        }
    }

    private void loadData() {
        try {
            var file = new File(Config.getInstance().getConfigDir(), "mfc-models.json");
            if (!file.exists()) {
                return;
            }
            var json = Files.readString(file.toPath());
            var data = new JSONArray(json);
            for (var i = 0; i < data.length(); i++) {
                createRow(data, i).ifPresent(observableModels::add);
            }
        } catch (Exception e) {
            log.debug("Couldn't read mfc models table data: {}", e.getMessage());
        }
    }

    private Optional<ModelTableRow> createRow(JSONArray data, int i) {
        try {
            var row = new ModelTableRow();
            var model = data.getJSONObject(i);
            row.uid = model.getInt("uid");
            row.blurp.set(model.optString("blurp"));
            row.camScore.set(model.optDouble("camScore"));
            row.continent.set(model.optString("continent"));
            row.country.set(model.optString("country"));
            row.ethnic.set(model.optString("ethnic"));
            row.name.set(model.optString("name"));
            row.newModel.set(model.optString("newModel"));
            row.occupation.set(model.optString("occupation"));
            row.state.set(model.optString("state"));
            row.tags.set(model.optString("tags"));
            row.topic.set(model.optString("topic"));
            return Optional.of(row);
        } catch (Exception e) {
            // ignore this error
        }
        return Optional.empty();
    }

    private void saveState() {
        var settings = Config.getInstance().getSettings();
        if (!table.getSortOrder().isEmpty()) {
            TableColumn<ModelTableRow, ?> col = table.getSortOrder().get(0);
            settings.mfcModelsTableSortColumn = col.getText();
            settings.mfcModelsTableSortType = col.getSortType().toString();
        }
        int size = table.getColumns().size();
        var columnWidths = new double[size];
        var columnIds = new String[size];
        for (var i = 0; i < columnWidths.length; i++) {
            columnWidths[i] = table.getColumns().get(i).getWidth();
            columnIds[i] = table.getColumns().get(i).getId();
        }
        settings.mfcModelsTableColumnWidths = columnWidths;
        settings.mfcModelsTableColumnIds = columnIds;
    }

    private void restoreState() {
        restoreColumnOrder();
        restoreColumnWidths();
        restoreSorting();

        filterInput.setText(Config.getInstance().getSettings().mfcModelsTableFilter);
    }

    private void restoreSorting() {
        String sortCol = Config.getInstance().getSettings().mfcModelsTableSortColumn;
        if (StringUtil.isNotBlank(sortCol)) {
            for (TableColumn<ModelTableRow, ?> col : table.getColumns()) {
                if (Objects.equals(sortCol, col.getText())) {
                    col.setSortType(SortType.valueOf(Config.getInstance().getSettings().mfcModelsTableSortType));
                    table.getSortOrder().clear();
                    table.getSortOrder().add(col);
                    break;
                }
            }
        }
    }

    private void restoreColumnOrder() {
        String[] columnIds = Config.getInstance().getSettings().mfcModelsTableColumnIds;
        ObservableList<TableColumn<ModelTableRow, ?>> tableColumns = table.getColumns();
        for (var i = 0; i < columnIds.length; i++) {
            for (var j = 0; j < table.getColumns().size(); j++) {
                if (Objects.equals(columnIds[i], tableColumns.get(j).getId())) {
                    TableColumn<ModelTableRow, ?> col = tableColumns.get(j);
                    tableColumns.remove(j); // NOSONAR
                    tableColumns.add(i, col);
                }
            }
        }
    }

    private void restoreColumnWidths() {
        double[] columnWidths = Config.getInstance().getSettings().mfcModelsTableColumnWidths;
        if (columnWidths != null && columnWidths.length == table.getColumns().size()) {
            for (var i = 0; i < columnWidths.length; i++) {
                table.getColumns().get(i).setPrefWidth(columnWidths[i]);
            }
        }
    }

    private ListChangeListener<TableColumn<ModelTableRow, ?>> createSortOrderChangedListener() {
        return c -> saveState();
    }

    private static class ModelTableRow {
        private Integer uid;
        private final StringProperty name = new SimpleStringProperty();
        private final StringProperty state = new SimpleStringProperty();
        private final DoubleProperty camScore = new SimpleDoubleProperty();
        private final StringProperty newModel = new SimpleStringProperty();
        private final StringProperty ethnic = new SimpleStringProperty();
        private final StringProperty country = new SimpleStringProperty();
        private final StringProperty continent = new SimpleStringProperty();
        private final StringProperty occupation = new SimpleStringProperty();
        private final StringProperty tags = new SimpleStringProperty();
        private final StringProperty blurp = new SimpleStringProperty();
        private final StringProperty topic = new SimpleStringProperty();
        private final BooleanProperty isHd = new SimpleBooleanProperty();
        private final BooleanProperty isWebrtc = new SimpleBooleanProperty();
        private final SimpleIntegerProperty uidProperty = new SimpleIntegerProperty();
        private final SimpleIntegerProperty flagsProperty = new SimpleIntegerProperty();

        public ModelTableRow(SessionState st) {
            update(st);
        }

        private ModelTableRow() {
        }

        public void update(SessionState st) {
            uid = st.getUid();
            uidProperty.set(uid);
            setProperty(name, Optional.ofNullable(st.getNm()));
            setProperty(state, Optional.ofNullable(st.getVs()).map(vs -> ctbrec.sites.mfc.State.of(vs).toString()));
            setProperty(camScore, Optional.ofNullable(st.getM()).map(ctbrec.sites.mfc.Model::getCamscore));
            Optional<Integer> isNew = Optional.ofNullable(st.getM()).map(ctbrec.sites.mfc.Model::getNewModel);
            isNew.ifPresent(integer -> newModel.set(integer == 1 ? "new" : ""));
            setProperty(ethnic, Optional.ofNullable(st.getU()).map(User::getEthnic));
            setProperty(country, Optional.ofNullable(st.getU()).map(User::getCountry));
            setProperty(continent, Optional.ofNullable(st.getM()).map(ctbrec.sites.mfc.Model::getContinent));
            setProperty(occupation, Optional.ofNullable(st.getU()).map(User::getOccupation));
            int flags = Optional.ofNullable(st.getM()).map(ctbrec.sites.mfc.Model::getFlags).orElse(0);
            isWebrtc.set((flags & 524288) == 524288);
            isHd.set(Optional.ofNullable(st.getU()).map(User::getPhase).orElse("z").equalsIgnoreCase("a"));
            flagsProperty.setValue(flags);
            Set<String> tagSet = Optional.ofNullable(st.getM()).map(ctbrec.sites.mfc.Model::getTags).orElse(Collections.emptySet());
            if (!tagSet.isEmpty()) {
                var sb = new StringBuilder();
                for (String t : tagSet) {
                    sb.append(t).append(',').append(' ');
                }
                tags.set(sb.substring(0, sb.length() - 2));
            }
            setProperty(blurp, Optional.ofNullable(st.getU()).map(User::getBlurb));
            String tpc = Optional.ofNullable(st.getM()).map(ctbrec.sites.mfc.Model::getTopic).orElse("n/a");
            tpc = URLDecoder.decode(tpc, UTF_8);
            topic.set(tpc);
        }

        private <T> void setProperty(Property<T> prop, Optional<T> value) {
            if (value.isPresent() && !Objects.equals(value.get(), prop.getValue())) {
                prop.setValue(value.get());
            }
        }

        public StringProperty nameProperty() {
            return name;
        }

        public StringProperty stateProperty() {
            return state;
        }

        public DoubleProperty camScoreProperty() {
            return camScore;
        }

        public StringProperty newModelProperty() {
            return newModel;
        }

        public StringProperty ethnicityProperty() {
            return ethnic;
        }

        public StringProperty countryProperty() {
            return country;
        }

        public StringProperty continentProperty() {
            return continent;
        }

        public StringProperty occupationProperty() {
            return occupation;
        }

        public StringProperty tagsProperty() {
            return tags;
        }

        public StringProperty blurpProperty() {
            return blurp;
        }

        public StringProperty topicProperty() {
            return topic;
        }

        public BooleanProperty hdProperty() {
            return isHd;
        }

        public BooleanProperty webrtcProperty() {
            return isWebrtc;
        }

        public IntegerProperty flagsProperty() {
            return flagsProperty;
        }

        public IntegerProperty uidProperty() {
            return uidProperty;
        }

        @Override
        public int hashCode() {
            final var prime = 31;
            var result = 1;
            result = prime * result + ((uid == null) ? 0 : uid.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            ModelTableRow other = (ModelTableRow) obj;
            if (uid == null) {
                return other.uid == null;
            } else return uid.equals(other.uid);
        }
    }
}
