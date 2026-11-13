package ctbrec.ui.settings;

import ctbrec.Config;
import ctbrec.io.json.mapper.PostProcessorMapper;
import ctbrec.recorder.postprocessing.*;
import ctbrec.ui.controls.Dialogs;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.mapstruct.factory.Mappers;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class PostProcessingStepPanel extends GridPane {

    private final Config config;

    private static final Class<?>[] POST_PROCESSOR_CLASSES = new Class<?>[]{ // @formatter: off
            Copy.class,
            Rename.class,
            Move.class,
            Remux.class,
            Script.class,
            DeleteOriginal.class,
            DeleteTooShort.class,
            RemoveKeepFile.class,
            CreateContactSheet.class
    }; // @formatter: on

    TableView<PostProcessor> stepView;
    ObservableList<PostProcessor> stepList;

    Button up;
    Button down;

    Button add;
    Button remove;
    Button edit;

    public PostProcessingStepPanel(Config config) {
        this.config = config;
        initGui();
    }

    private void initGui() {
        setHgap(5);
        vgapProperty().bind(hgapProperty());

        up = createUpButton();
        down = createDownButton();
        add = createAddButton();
        remove = createRemoveButton();
        edit = createEditButton();
        var buttons = new VBox(5, add, edit, up, down, remove);

        List<PostProcessor> postProcessors = config.getSettings().postProcessors
                .stream()
                .map(Mappers.getMapper(PostProcessorMapper.class)::toPostProcessor)
                .collect(Collectors.toList()); // NOSONAR - toList returns an unmodifiable list
        stepList = FXCollections.observableList(postProcessors);
        stepList.addListener((ListChangeListener<PostProcessor>) change -> safelySaveConfig());
        stepView = new TableView<>(stepList);
        stepView.setEditable(true);
        stepView.setStyle("-fx-table-cell-border-color: transparent;");
        var postProcessorColumn = new TableColumn<PostProcessor, String>("Step");
        postProcessorColumn.setCellValueFactory(cdf -> new SimpleStringProperty(cdf.getValue().getName()));
        postProcessorColumn.setStyle("-fx-alignment: CENTER-LEFT;");
        var enabledColumn = new TableColumn<PostProcessor, Boolean>("Enabled");
        enabledColumn.setCellValueFactory(cdf -> {
            var prop = new SimpleBooleanProperty(cdf.getValue().isEnabled());
            prop.addListener((src, oldV, newV) -> {
                cdf.getValue().setEnabled(newV);
                safelySaveConfig();
            });
            return prop;
        });
        enabledColumn.setCellFactory(CheckBoxTableCell.forTableColumn(enabledColumn));
        enabledColumn.setEditable(true);
        enabledColumn.setStyle("-fx-alignment: CENTER;");
        postProcessorColumn.prefWidthProperty().bind(stepView.widthProperty().subtract(enabledColumn.widthProperty()).subtract(5));
        stepView.getColumns().add(enabledColumn);
        stepView.getColumns().add(postProcessorColumn);
        GridPane.setHgrow(stepView, Priority.ALWAYS);
        GridPane.setFillWidth(stepView, true);

        add(stepView, 0, 0);
        add(buttons, 1, 0);

        stepView.getSelectionModel().selectedIndexProperty().addListener((obs, oldV, newV) -> {
            var idx = newV.intValue();
            boolean noSelection = idx == -1;
            up.setDisable(noSelection || idx == 0);
            down.setDisable(noSelection || idx == stepList.size() - 1);
            edit.setDisable(noSelection);
            remove.setDisable(noSelection);
        });

        setPadding(new Insets(10));
    }

    private void safelySaveConfig() {
        try {
            config.getSettings().postProcessors = stepList.stream()
                    .map(Mappers.getMapper(PostProcessorMapper.class)::toDto)
                    .collect(Collectors.toList()); // NOSONAR - toList returns an unmodifiable list
            config.save();
        } catch (IOException e) {
            Dialogs.showError(getScene(), "Couldn't save configuration", "An error occurred while saving the configuration", e);
        }
    }

    private Button createUpButton() {
        var button = createButton("▴", "Move step up");
        button.setOnAction(evt -> {
            int idx = stepView.getSelectionModel().getSelectedIndex();
            PostProcessor selectedItem = stepView.getSelectionModel().getSelectedItem();
            stepList.remove(idx);
            stepList.add(idx - 1, selectedItem);
            stepView.getSelectionModel().select(idx - 1);
        });
        return button;
    }

    private Button createDownButton() {
        var button = createButton("▾", "Move step down");
        button.setOnAction(evt -> {
            int idx = stepView.getSelectionModel().getSelectedIndex();
            PostProcessor selectedItem = stepView.getSelectionModel().getSelectedItem();
            stepList.remove(idx);
            stepList.add(idx + 1, selectedItem);
            stepView.getSelectionModel().select(idx + 1);
        });
        return button;
    }

    private Button createAddButton() {
        var button = createButton("+", "Add a new step");
        button.setDisable(false);
        button.setOnAction(evt -> {
            PostProcessor[] options = createOptions();
            ChoiceDialog<PostProcessor> choice = new ChoiceDialog<>(options[0], options);
            choice.setTitle("New Post-Processing Step");
            choice.setHeaderText("Select the new step type");
            choice.setResizable(true);
            choice.setWidth(600);
            choice.getDialogPane().setMinWidth(400);
            var stage = (Stage) choice.getDialogPane().getScene().getWindow();
            stage.getScene().getStylesheets().addAll(getScene().getStylesheets());
            InputStream icon = Dialogs.class.getResourceAsStream("/icon.png");
            stage.getIcons().add(new Image(icon));

            Optional<PostProcessor> result = choice.showAndWait();
            result.ifPresent(pp -> PostProcessingDialogFactory.openNewDialog(pp, getScene(), stepList));
            safelySaveConfig();
        });
        return button;
    }

    private PostProcessor[] createOptions() {
        try {
            var options = new PostProcessor[POST_PROCESSOR_CLASSES.length];
            for (var i = 0; i < POST_PROCESSOR_CLASSES.length; i++) {
                Class<?> cls = POST_PROCESSOR_CLASSES[i];
                PostProcessor pp;
                pp = (PostProcessor) cls.getDeclaredConstructor().newInstance();
                options[i] = pp;
            }
            return options;
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
                 | SecurityException e) {
            Dialogs.showError(getScene(), "Create post-processor selection", "Error while reaing in post-processing options", e);
            return new PostProcessor[0];
        }
    }

    private Button createRemoveButton() {
        var button = createButton("-", "Remove selected step");
        button.setOnAction(evt -> {
            PostProcessor selectedItem = stepView.getSelectionModel().getSelectedItem();
            if (selectedItem != null) {
                stepList.remove(selectedItem);
            }
        });
        return button;
    }

    private Button createEditButton() {
        var button = createButton("✎", "Edit selected step");
        button.setOnAction(evt -> {
            PostProcessor selectedItem = stepView.getSelectionModel().getSelectedItem();
            PostProcessingDialogFactory.openEditDialog(selectedItem, getScene(), stepList);
            stepView.refresh();
            safelySaveConfig();
        });
        return button;
    }

    private Button createButton(String text, String tooltip) {
        var b = new Button(text);
        b.setTooltip(new Tooltip(tooltip));
        b.setDisable(true);
        b.setPrefSize(32, 32);
        return b;
    }
}
