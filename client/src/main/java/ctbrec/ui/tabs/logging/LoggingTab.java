package ctbrec.ui.tabs.logging;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.eventbus.Subscribe;

import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ctbrec.Config;
import ctbrec.event.EventBusHolder;
import ctbrec.ui.controls.CustomMouseBehaviorContextMenu;
import ctbrec.ui.controls.SearchBox;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;

public class LoggingTab extends Tab implements TabSelectionListener {

    private static final int HISTORY_LENGTH = 10_000;
    private SearchBox filter = new SearchBox();
    private TableView<LoggingEvent> table = new TableView<>();
    private ObservableList<LoggingEvent> history = FXCollections.observableList(Collections.synchronizedList(new LinkedList<>()));
    private ObservableList<LoggingEvent> filteredEvents = FXCollections.observableArrayList();
    private List<LoggingEvent> eventBuffer = new LinkedList<>();
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private volatile boolean tabClosed = false;
    private volatile boolean tabSelected = false;
    private ContextMenu popup;
    private Object eventBustSubscriber = new Object() {
        @Subscribe
        public void publishLoggingEevent(LoggingEvent event) {
            if (!tabClosed) {
                if (tabSelected) {
                    Platform.runLater(() -> {
                        history.add(event);
                        if (history.size() > HISTORY_LENGTH - 1) {
                            history.remove(0);
                        }
                        filter();
                    });
                } else {
                    eventBuffer.add(event);
                    if (eventBuffer.size() > HISTORY_LENGTH - 1) {
                        eventBuffer.remove(0);
                    }
                }
            }
        }
    };

    private void filter() {
        filteredEvents.clear();
        filteredEvents.addAll(history.stream().filter(evt -> {
            String q = filter.getText().toLowerCase();
            return evt.getLevel().toString().toLowerCase().contains(q)
                    || createLogMessage(evt).toLowerCase().contains(q);
        }).collect(Collectors.toList()));
    }

    public LoggingTab() {
        setText("Logging");
        subscribeToEventBus();

        Config config = Config.getInstance();
        table = new TableView<>(filteredEvents);
        if (!config.getSettings().showGridLinesInTables) {
            table.setStyle("-fx-table-cell-border-color: transparent;");
        }

        var idx = 0;
        TableColumn<LoggingEvent, String> level = createTableColumn("Level", 65, idx++);
        level.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getLevel().toString()));
        table.getColumns().add(level);

        TableColumn<LoggingEvent, String> time = createTableColumn("Timestamp", 200, idx++);
        time.setCellValueFactory(cdf -> {
            var instant = Instant.ofEpochMilli(cdf.getValue().getTimeStamp());
            return new SimpleStringProperty(instant.atZone(ZoneId.systemDefault()).format(timeFormatter));
        });
        table.getColumns().add(time);

        TableColumn<LoggingEvent, String> location = createTableColumn("Location", 250, idx++);
        location.setCellValueFactory(cdf -> {
            if(cdf.getValue().getCallerData().length > 0) {
                StackTraceElement loc = cdf.getValue().getCallerData()[0];
                String l = loc.getFileName() + ":" + loc.getLineNumber();
                return new SimpleStringProperty(l);
            } else {
                return new SimpleStringProperty("");
            }
        });
        table.getColumns().add(location);

        TableColumn<LoggingEvent, String> msg = createTableColumn("Message", 2000, idx++);
        msg.setCellValueFactory(cdf -> new SimpleStringProperty(createLogMessage(cdf.getValue())));
        table.getColumns().add(msg);

        var layout = new BorderPane();
        BorderPane.setMargin(table, new Insets(10));
        BorderPane.setMargin(filter, new Insets(10, 10, 0, 10));
        layout.setCenter(table);
        layout.setTop(filter);

        setContent(layout);
        setOnClosed(evt -> {
            EventBusHolder.BUS.unregister(eventBustSubscriber);
            tabClosed = true;
        });

        filter.setPromptText("Search");
        filter.textProperty().addListener( (observableValue, oldValue, newValue) -> filter());

        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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
    }

    private ContextMenu createContextMenu() {
        final ObservableList<LoggingEvent> selectedEvents = table.getSelectionModel().getSelectedItems();
        if (selectedEvents.isEmpty()) {
            return null;
        }
        var copy = new MenuItem("Copy");
        copy.setOnAction(e -> Platform.runLater(() -> {
            String formattedMessages = getFormattedMessages(selectedEvents);
            final var content = new ClipboardContent();
            content.putString(formattedMessages);
            Clipboard.getSystemClipboard().setContent(content);
        }));

        ContextMenu menu = new CustomMouseBehaviorContextMenu(copy);
        return menu;
    }

    private String getFormattedMessages(ObservableList<LoggingEvent> selectedEvents) {
        var sb = new StringBuilder();

        var rootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var loggerContext = rootLogger.getLoggerContext();
        loggerContext.reset();

        var encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern("%date %level [%thread] %logger{10} [%file:%line] %msg%n");
        encoder.start();

        for (LoggingEvent evt : selectedEvents) {
            byte[] encode = encoder.encode(evt);
            sb.append(new String(encode));
        }
        return sb.toString();
    }

    private String createLogMessage(LoggingEvent evt) {
        var sb = new StringBuilder(evt.getFormattedMessage());
        IThrowableProxy throwableProxy = evt.getThrowableProxy();
        while (throwableProxy != null) {
            sb.append('\n').append(throwableProxy.getClassName()).append(':').append(' ').append(throwableProxy.getMessage());
            for (StackTraceElementProxy step : throwableProxy.getStackTraceElementProxyArray()) {
                sb.append('\n').append('\t').append(step.getSTEAsString());
            }
            throwableProxy = throwableProxy.getCause();
            if (throwableProxy != null) {
                sb.append("\nCaused by: ");
            }
        }
        return sb.toString();
    }

    private <T> TableColumn<LoggingEvent, T> createTableColumn(String text, int width, int idx) {
        TableColumn<LoggingEvent, T> tc = new TableColumn<>(text);
        tc.setPrefWidth(width);
        tc.setUserData(idx);
        return tc;
    }

    private void subscribeToEventBus() {
        EventBusHolder.BUS.register(eventBustSubscriber);
    }

    @Override
    public void selected() {
        history.addAll(eventBuffer);
        tabSelected = true;
        while (history.size() > HISTORY_LENGTH - 1) {
            history.remove(0);
        }
        filter();
        eventBuffer.clear();
    }

    @Override
    public void deselected() {
        tabSelected = false;
    }
}
