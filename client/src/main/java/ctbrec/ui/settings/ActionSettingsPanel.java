package ctbrec.ui.settings;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.StringUtil;
import ctbrec.event.*;
import ctbrec.event.EventHandlerConfiguration.ActionConfiguration;
import ctbrec.event.EventHandlerConfiguration.PredicateConfiguration;
import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.recorder.Recorder;
import ctbrec.ui.CamrecApplication;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.FileSelectionBox;
import ctbrec.ui.controls.ProgramSelectionBox;
import ctbrec.ui.controls.Wizard;
import ctbrec.ui.event.PlaySound;
import ctbrec.ui.event.ShowNotification;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ActionSettingsPanel extends GridPane {
    private static final Logger LOG = LoggerFactory.getLogger(ActionSettingsPanel.class);
    private ListView<EventHandlerConfiguration> actionTable;

    private final TextField name = new TextField();
    private final ComboBox<Event.Type> event = new ComboBox<>();
    private final ComboBox<Model.State> modelState = new ComboBox<>();
    private final ComboBox<Recording.State> recordingState = new ComboBox<>();

    private final CheckBox playSound = new CheckBox("Play sound");
    private final FileSelectionBox sound = new FileSelectionBox();
    private final Slider soundVolume = new Slider(0, 100, 100);
    private final CheckBox showNotification = new CheckBox("Notify me");
    private final Button testNotification = new Button("Test");
    private final ToggleButton toggleEvents = new ToggleButton();
    private final CheckBox executeProgram = new CheckBox("Execute program");
    private final ProgramSelectionBox program = new ProgramSelectionBox();
    private ListSelectionPane<Model> modelSelectionPane;

    private final Recorder recorder;

    public ActionSettingsPanel(Recorder recorder) {
        this.recorder = recorder;
        createGui();
        loadEventHandlers();
    }

    private void loadEventHandlers() {
        actionTable.getItems().addAll(Config.getInstance().getSettings().eventHandlers);
    }

    private void createGui() {
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20, 10, 10, 10));

        var headline = new Label("Events & Actions");
        headline.getStyleClass().add("settings-group-label");
        add(headline, 0, 0);

        actionTable = createActionTable();
        var scrollPane = new ScrollPane(actionTable);
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: -fx-background");
        add(scrollPane, 0, 1);
        GridPane.setHgrow(scrollPane, Priority.ALWAYS);

        var add = new Button("Add");
        add.setOnAction(this::add);
        var delete = new Button("Delete");
        delete.setOnAction(this::delete);
        delete.setDisable(true);
        toggleEvents.setOnAction(this::toggleEvents);
        toggleEvents.setSelected(Config.getInstance().getSettings().eventsSuspended);
        toggleEvents.setText(toggleEvents.isSelected() ? "Resume Events" : "Suspend Events");
        toggleEvents.setTooltip(new Tooltip(toggleEvents.isSelected() ? "Events are currently suspended" : "Events are currently active"));
        var buttons = new HBox(5, add, delete, toggleEvents);
        buttons.setStyle("-fx-background-color: -fx-background"); // workaround so that the buttons don't shrink
        add(buttons, 0, 2);

        actionTable.getSelectionModel().getSelectedItems().addListener((ListChangeListener<EventHandlerConfiguration>) change -> delete.setDisable(change.getList().isEmpty()));
    }

    private void toggleEvents(ActionEvent actionEvent) {
        Config.getInstance().getSettings().eventsSuspended = toggleEvents.isSelected();
        toggleEvents.setText(toggleEvents.isSelected() ? "Resume Events" : "Suspend Events");
        toggleEvents.setTooltip(new Tooltip(toggleEvents.isSelected() ? "Events are currently suspended" : "Events are currently active"));
        try {
            Config.getInstance().save();
        } catch (IOException e) {
            LOG.error("Couldn't save config", e);
        }
    }

    private void add(ActionEvent evt) {
        var actionPane = createActionPane();
        var dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.initOwner(getScene().getWindow());
        dialog.setTitle("New Action");
        InputStream icon = Objects.requireNonNull(getClass().getResourceAsStream("/icon.png"), "/icon.png not found in classpath");
        dialog.getIcons().add(new Image(icon));
        var root = new Wizard(dialog, this::validateSettings, actionPane);
        var scene = new Scene(root, 800, 540);
        scene.getStylesheets().addAll(getScene().getStylesheets());
        dialog.setScene(scene);
        centerOnParent(dialog);
        dialog.showAndWait();
        if (!root.isCancelled()) {
            createEventHandler();
        }
    }

    private void createEventHandler() {
        var config = new EventHandlerConfiguration();
        config.setName(name.getText());
        config.setEvent(event.getValue());
        if (event.getValue() == Event.Type.MODEL_STATUS_CHANGED) {
            var pc = new PredicateConfiguration();
            pc.setType(ModelStatePredicate.class.getName());
            pc.getConfiguration().put("state", modelState.getValue().name());
            pc.setName("state = " + modelState.getValue().toString());
            config.getPredicates().add(pc);
        } else if (event.getValue() == Event.Type.RECORDING_STATUS_CHANGED) {
            var pc = new PredicateConfiguration();
            pc.setType(RecordingStatePredicate.class.getName());
            pc.getConfiguration().put("state", recordingState.getValue().name());
            pc.setName("state = " + recordingState.getValue().toString());
            config.getPredicates().add(pc);
        } else if (event.getValue() == Event.Type.NO_SPACE_LEFT) {
            var pc = new PredicateConfiguration();
            pc.setType(MatchAllPredicate.class.getName());
            pc.setName("no space left");
            config.getPredicates().add(pc);
        }
        if (!modelSelectionPane.isAllSelected()) {
            var pc = new PredicateConfiguration();
            pc.setType(ModelPredicate.class.getName());
            pc.setModels(modelSelectionPane.getSelectedItems().stream().map(Mappers.getMapper(ModelMapper.class)::toDto).collect(Collectors.toList())); // NOSONAR
            pc.setName("model is one of:" + modelSelectionPane.getSelectedItems());
            config.getPredicates().add(pc);
        }
        if (showNotification.isSelected()) {
            var ac = new ActionConfiguration();
            ac.setType(ShowNotification.class.getName());
            ac.setName("show notification");
            config.getActions().add(ac);
        }
        if (playSound.isSelected()) {
            var ac = new ActionConfiguration();
            ac.setType(PlaySound.class.getName());
            var file = new File(sound.fileProperty().get());
            ac.getConfiguration().put("file", file.getAbsolutePath());
            ac.getConfiguration().put("volume", soundVolume.getValue() / 100);
            ac.setName("play " + file.getName());
            config.getActions().add(ac);
        }
        if (executeProgram.isSelected()) {
            var ac = new ActionConfiguration();
            ac.setType(ExecuteProgram.class.getName());
            var file = new File(program.fileProperty().get());
            ac.getConfiguration().put("file", file.getAbsolutePath());
            ac.setName("execute " + file.getName());
            config.getActions().add(ac);
        }

        var handler = new EventHandler(config);
        EventBusHolder.register(handler);
        Config.getInstance().getSettings().eventHandlers.add(config);
        actionTable.getItems().add(config);
        LOG.debug("Registered event handler for {} {}", config.getEvent(), config.getName());
    }

    private void validateSettings() {
        if (StringUtil.isBlank(name.getText())) {
            throw new IllegalStateException("Name cannot be empty");
        }
        if (event.getValue() == Event.Type.MODEL_STATUS_CHANGED && modelState.getValue() == null) {
            throw new IllegalStateException("Select a state");
        }
        if (event.getValue() == Event.Type.RECORDING_STATUS_CHANGED && recordingState.getValue() == null) {
            throw new IllegalStateException("Select a state");
        }
        if (event.getValue() != Event.Type.NO_SPACE_LEFT && modelSelectionPane.getSelectedItems().isEmpty() && !modelSelectionPane.isAllSelected()) {
            throw new IllegalStateException("Select one or more models or tick off \"all\"");
        }
        if (!(showNotification.isSelected() || playSound.isSelected() || executeProgram.isSelected())) {
            throw new IllegalStateException("No action selected");
        }
    }

    private void delete(ActionEvent evt) {
        List<EventHandlerConfiguration> selected = new ArrayList<>(actionTable.getSelectionModel().getSelectedItems());
        for (EventHandlerConfiguration config : selected) {
            EventBusHolder.unregister(config.getId());
            Config.getInstance().getSettings().eventHandlers.remove(config);
            actionTable.getItems().remove(config);
        }
    }

    private Pane createActionPane() {
        GridPane layout = SettingsTab.createGridLayout();
        recordingState.prefWidthProperty().bind(event.widthProperty());
        modelState.prefWidthProperty().bind(event.widthProperty());
        name.prefWidthProperty().bind(event.widthProperty());

        var row = 0;
        layout.add(new Label("Name"), 0, row);
        layout.add(name, 1, row++);

        layout.add(new Label("Event"), 0, row);
        event.getItems().clear();
        event.getItems().add(Event.Type.MODEL_STATUS_CHANGED);
        event.getItems().add(Event.Type.RECORDING_STATUS_CHANGED);
        event.getItems().add(Event.Type.NO_SPACE_LEFT);
        event.setOnAction(evt -> modelState.setVisible(event.getSelectionModel().getSelectedItem() == Event.Type.MODEL_STATUS_CHANGED));
        event.getSelectionModel().select(Event.Type.MODEL_STATUS_CHANGED);
        layout.add(event, 1, row++);

        event.getSelectionModel().selectedItemProperty().addListener((obs, oldV, newV) -> {
            var modelRelatedStuffDisabled = false;
            if (newV == Event.Type.NO_SPACE_LEFT) {
                modelRelatedStuffDisabled = true;
                modelSelectionPane.selectAll();
            }

            modelState.setDisable(modelRelatedStuffDisabled);
            recordingState.setDisable(modelRelatedStuffDisabled);
            modelSelectionPane.setDisable(modelRelatedStuffDisabled);
        });

        layout.add(new Label("State"), 0, row);
        modelState.getItems().clear();
        modelState.getItems().addAll(Model.State.values());
        layout.add(modelState, 1, row);
        recordingState.getItems().clear();
        recordingState.getItems().addAll(Recording.State.values());
        layout.add(recordingState, 1, row++);
        recordingState.visibleProperty().bind(modelState.visibleProperty().not());

        layout.add(createSeparator(), 0, row++);

        var l = new Label("Models");
        layout.add(l, 0, row);
        modelSelectionPane = new ListSelectionPane<>(recorder.getModels(), Collections.emptyList());
        layout.add(modelSelectionPane, 1, row++);
        GridPane.setValignment(l, VPos.TOP);
        GridPane.setHgrow(modelSelectionPane, Priority.ALWAYS);
        GridPane.setFillWidth(modelSelectionPane, true);

        layout.add(createSeparator(), 0, row++);

        layout.add(showNotification, 0, row);
        layout.add(testNotification, 1, row++);
        testNotification.setOnAction(evt -> {
            var format = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM);
            var time = ZonedDateTime.now();
            DesktopIntegration.notification(CamrecApplication.title, "Test Notification", "Oi, what's up! " + format.format(time));
        });
        testNotification.disableProperty().bind(showNotification.selectedProperty().not());

        HBox soundContainer = new HBox();
        soundContainer.setAlignment(Pos.CENTER_LEFT);
        soundContainer.setSpacing(5);
        layout.add(playSound, 0, row);
        layout.add(soundContainer, 1, row++);
        soundContainer.getChildren().add(soundVolume);
        soundContainer.getChildren().add(sound);
        sound.disableProperty().bind(playSound.selectedProperty().not());
        soundVolume.setTooltip(new Tooltip("Volume"));
        soundVolume.disableProperty().bind(playSound.selectedProperty().not());
        HBox.setHgrow(sound, Priority.ALWAYS);
        Button soundTest = new Button("Test");
        soundTest.setOnAction(this::testSound);
        soundContainer.getChildren().add(soundTest);

        layout.add(executeProgram, 0, row);
        layout.add(program, 1, row);
        program.disableProperty().bind(executeProgram.selectedProperty().not());

        GridPane.setFillWidth(name, true);
        GridPane.setHgrow(name, Priority.ALWAYS);
        return layout;
    }

    private void testSound(ActionEvent actionEvent) {
        try {
            URL soundFileUrl = new File(sound.fileProperty().getValue()).toURI().toURL();
            new PlaySound(soundFileUrl, soundVolume.getValue() / 100).accept(null);
        } catch (MalformedURLException e) {
            Dialogs.showError(getScene(), "Error", e.getLocalizedMessage(), e);
        }
    }

    private ListView<EventHandlerConfiguration> createActionTable() {
        ListView<EventHandlerConfiguration> view = new ListView<>();
        view.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        return view;
    }

    private Node createSeparator() {
        var divider = new Separator(Orientation.HORIZONTAL);
        GridPane.setHgrow(divider, Priority.ALWAYS);
        GridPane.setFillWidth(divider, true);
        GridPane.setColumnSpan(divider, 2);
        var tb = 20;
        var lr = 0;
        GridPane.setMargin(divider, new Insets(tb, lr, tb, lr));
        return divider;
    }

    private void centerOnParent(Stage dialog) {
        dialog.setWidth(dialog.getScene().getWidth());
        dialog.setHeight(dialog.getScene().getHeight());
        double w = dialog.getWidth();
        double h = dialog.getHeight();
        Window p = dialog.getOwner();
        double px = p.getX();
        double py = p.getY();
        double pw = p.getWidth();
        double ph = p.getHeight();
        dialog.setX(px + (pw - w) / 2);
        dialog.setY(py + (ph - h) / 2);
    }
}
