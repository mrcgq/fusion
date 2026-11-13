package ctbrec.ui.tabs;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.eventbus.Subscribe;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.event.EventBusHolder;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.sites.SiteUtil;
import ctbrec.ui.ShutdownListener;
import ctbrec.ui.action.PlayAction;
import ctbrec.ui.controls.CustomMouseBehaviorContextMenu;
import ctbrec.ui.controls.DateTimeCellFactory;
import ctbrec.ui.controls.SearchBox;
import ctbrec.ui.event.PlayerStartedEvent;
import ctbrec.ui.io.json.dto.PlayerStartedEventDto;
import ctbrec.ui.io.json.mapper.PlayerStartedEventMapper;
import ctbrec.ui.menu.ModelMenuContributor;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.control.TableColumn.SortType;
import javafx.scene.input.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.Callback;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

@Slf4j
public class RecentlyWatchedTab extends Tab implements ShutdownListener {

    private final ObservableList<PlayerStartedEvent> filteredModels = FXCollections.observableArrayList();
    private final ObservableList<PlayerStartedEvent> observableModels = FXCollections.observableArrayList();
    private final TableView<PlayerStartedEvent> table = new TableView<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Recorder recorder;
    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();
    private ContextMenu popup;
    private List<Site> sites;

    public RecentlyWatchedTab(Recorder recorder, List<Site> sites) {
        this.recorder = recorder;
        this.sites = sites;
        setText("Recently Watched");
        createGui();
        loadHistory();
        subscribeToPlayerEvents();
        setOnClosed(evt -> onShutdown());
    }

    private void createGui() {
        Config config = Config.getInstance();
        var layout = new BorderPane();
        layout.setPadding(new Insets(5, 10, 10, 10));

        var filterInput = new SearchBox(false);
        filterInput.setPromptText("Filter");
        filterInput.textProperty().addListener((observableValue, oldValue, newValue) -> {
            String filter = filterInput.getText();
            lock.lock();
            try {
                filter(filter);
            } finally {
                lock.unlock();
            }
        });
        filterInput.getStyleClass().remove("search-box-icon");
        HBox.setHgrow(filterInput, Priority.ALWAYS);
        var topBar = new HBox(5);
        topBar.getChildren().addAll(filterInput);
        layout.setTop(topBar);
        BorderPane.setMargin(topBar, new Insets(0, 0, 5, 0));

        table.setItems(observableModels);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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
        table.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            List<PlayerStartedEvent> selectedModels = table.getSelectionModel().getSelectedItems();
            if (event.getCode() == KeyCode.DELETE) {
                delete(selectedModels);
            }
        });

        var idx = 0;
        TableColumn<PlayerStartedEvent, String> name = createTableColumn("Model", 200, idx++);
        name.setId("name");
        name.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getModel().getDisplayName()));
        name.setCellFactory(new ClickableCellFactory<>());
        table.getColumns().add(name);

        TableColumn<PlayerStartedEvent, String> url = createTableColumn("URL", 400, idx);
        url.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getModel().getUrl()));
        url.setCellFactory(new ClickableCellFactory<>());
        url.setEditable(false);
        url.setId("url");
        table.getColumns().add(url);

        TableColumn<PlayerStartedEvent, Instant> timestamp = createTableColumn("Timestamp", 150, idx);
        timestamp.setId("timestamp");
        timestamp.setCellValueFactory(cdf -> new SimpleObjectProperty<>(cdf.getValue().getTimestamp()));
        timestamp.setCellFactory(new DateTimeCellFactory<>(config.getDateTimeFormatter()));
        timestamp.setEditable(false);
        timestamp.setSortType(SortType.DESCENDING);
        table.getColumns().add(timestamp);
        table.getSortOrder().add(timestamp);

        var scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setContent(table);
        scrollPane.setStyle("-fx-background-color: -fx-background");
        layout.setCenter(scrollPane);
        setContent(layout);
    }

    private <T> TableColumn<PlayerStartedEvent, T> createTableColumn(String text, int width, int idx) {
        TableColumn<PlayerStartedEvent, T> tc = new TableColumn<>(text);
        tc.setPrefWidth(width);
        tc.setUserData(idx);
        return tc;
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
                for (TableColumn<PlayerStartedEvent, ?> tc : table.getColumns()) {
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
                    PlayerStartedEvent sessionState = table.getItems().get(i);
                    filteredModels.add(sessionState);
                }
            }
            observableModels.removeAll(filteredModels);
        } finally {
            lock.unlock();
        }
    }

    private ContextMenu createContextMenu() {
        ObservableList<PlayerStartedEvent> selectedRows = table.getSelectionModel().getSelectedItems();
        if (selectedRows.isEmpty()) {
            return null;
        }

        List<Model> selectedModels = table.getSelectionModel().getSelectedItems().stream().map(PlayerStartedEvent::getModel).collect(Collectors.toList());
        ContextMenu menu = new CustomMouseBehaviorContextMenu();

        ModelMenuContributor.newContributor(getTabPane(), Config.getInstance(), recorder) //
                .withStartStopCallback(m -> getTabPane().setCursor(Cursor.DEFAULT)) //
                .afterwards(table::refresh)
                .contributeToMenu(selectedModels, menu);

        menu.getItems().add(new SeparatorMenuItem());
        var delete = new MenuItem("Delete");
        delete.setOnAction(e -> delete(selectedRows));
        var clearHistory = new MenuItem("Clear history");
        clearHistory.setOnAction(e -> clearHistory());
        menu.getItems().addAll(delete, clearHistory);

        return menu;
    }

    private void clearHistory() {
        observableModels.clear();
        filteredModels.clear();
    }

    private void delete(List<PlayerStartedEvent> selectedRows) {
        observableModels.removeAll(selectedRows);
    }

    private void subscribeToPlayerEvents() {
        EventBusHolder.BUS.register(new Object() {
            @Subscribe
            public void handleEvent(PlayerStartedEvent evt) {
                table.getItems().add(evt);
                table.sort();
            }
        });
    }

    private class ClickableCellFactory<S, T> implements Callback<TableColumn<S, T>, TableCell<S, T>> {
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
                    var selectedModel = table.getSelectionModel().getSelectedItem().getModel();
                    if (selectedModel != null) {
                        new PlayAction(table, selectedModel).execute();
                    }
                }
            });
            return cell;
        }
    }

    private void saveHistory() throws IOException {
        String json = mapper.writeValueAsString(observableModels.stream().map(Mappers.getMapper(PlayerStartedEventMapper.class)::toDto).toList());
        var recentlyWatched = new File(Config.getInstance().getConfigDir(), "recently_watched.json");
        log.debug("Saving recently watched models to {}", recentlyWatched.getAbsolutePath());
        Files.createDirectories(recentlyWatched.getParentFile().toPath());
        Files.writeString(recentlyWatched.toPath(), json, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    private void loadHistory() {
        var recentlyWatched = new File(Config.getInstance().getConfigDir(), "recently_watched.json");
        if (!recentlyWatched.exists()) {
            return;
        }

        log.debug("Loading recently watched models from {}", recentlyWatched.getAbsolutePath());
        try {
            List<PlayerStartedEventDto> fromJson = mapper.readValue(Files.readString(recentlyWatched.toPath(), UTF_8), new TypeReference<List<PlayerStartedEventDto>>() {
            });
            observableModels.addAll(fromJson.stream()
                    .map(Mappers.getMapper(PlayerStartedEventMapper.class)::toEvent)
                    .toList());
            observableModels.forEach(evt -> SiteUtil.getSiteForModel(sites, evt.getModel()).ifPresent(evt.getModel()::setSite));
        } catch (IOException e) {
            log.error("Couldn't load recently watched models", e);
        }
    }

    @Override
    public void onShutdown() {
        try {
            saveHistory();
        } catch (IOException e) {
            log.error("Couldn't safe recently watched models", e);
        }
    }
}
