package ctbrec.ui.settings;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.Config;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.ui.AutosizeAlert;
import ctbrec.ui.controls.Dialogs;
import javafx.geometry.Insets;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.FileChooser;
import lombok.extern.slf4j.Slf4j;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;

import static javafx.scene.control.ButtonType.NO;
import static javafx.scene.control.ButtonType.YES;

@Slf4j
public class IgnoreList extends GridPane {

    private ListView<String> ignoreListView;

    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();

    public IgnoreList() {
        createGui();
        loadIgnoredModels();
    }

    private void createGui() {
        setHgap(10);
        setVgap(10);
        setPadding(new Insets(20, 10, 10, 10));

        ignoreListView = new ListView<>();
        ignoreListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        ignoreListView.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.DELETE) {
                removeSelectedModels();
            }
        });
        add(ignoreListView, 0, 0);
        GridPane.setHgrow(ignoreListView, Priority.ALWAYS);

        var remove = new Button("Remove");
        remove.setOnAction(evt -> removeSelectedModels());
        var exportIgnoreList = new Button("Export");
        exportIgnoreList.setOnAction(e -> exportIgnoreList());
        var importIgnoreList = new Button("Import");
        importIgnoreList.setOnAction(e -> importIgnoreList());
        var buttons = new HBox(10, remove, exportIgnoreList, importIgnoreList);
        add(buttons, 0, 2);
        buttons.setStyle("-fx-background-color: -fx-background"); // workaround so that the buttons don't shrink
    }

    private void removeSelectedModels() {
        List<String> selectedModels = ignoreListView.getSelectionModel().getSelectedItems();
        if (!selectedModels.isEmpty()) {
            Config.getInstance().getSettings().ignoredModels.removeAll(selectedModels);
            ignoreListView.getItems().removeAll(selectedModels);
            log.debug(Config.getInstance().getSettings().ignoredModels.toString());
            try {
                Config.getInstance().save();
            } catch (IOException e) {
                log.warn("Couldn't save config", e);
            }
        }
    }

    private void loadIgnoredModels() {
        List<String> ignored = Config.getInstance().getSettings().ignoredModels;
        ignoreListView.getItems().clear();
        ignoreListView.getItems().addAll(ignored);
        Collections.sort(ignoreListView.getItems());
    }

    public void refresh() {
        loadIgnoredModels();
    }

    private void exportIgnoreList() {
        var chooser = new FileChooser();
        chooser.setTitle("Export ignore list");
        chooser.setInitialFileName("ctbrec-ignorelist.json");
        var file = chooser.showSaveDialog(null);
        if (file != null) {
            try (var out = new FileOutputStream(file)) {
                String json = mapper.writeValueAsString(Config.getInstance().getSettings().ignoredModels);
                out.write(json.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                Dialogs.showError(getScene(), "Couldn't export ignore list", e.getLocalizedMessage(), e);
            }
        }
    }

    private void importIgnoreList() {
        var chooser = new FileChooser();
        chooser.setTitle("Import ignore list");
        var file = chooser.showOpenDialog(null);
        if (file != null) {
            try {
                String fileContent = Files.readString(file.toPath());
                List<String> ignoredModels = mapper.readValue(fileContent, new TypeReference<>() {
                });
                var confirmed = true;
                if (!Config.getInstance().getSettings().ignoredModels.isEmpty()) {
                    var msg = "This will replace the existing ignore list! Continue?";
                    var confirm = new AutosizeAlert(AlertType.CONFIRMATION, msg, getScene(), YES, NO);
                    confirm.setTitle("Import ignore list");
                    confirm.setHeaderText("Overwrite ignore list");
                    confirm.showAndWait();
                    confirmed = confirm.getResult() == ButtonType.YES;
                }
                if (confirmed) {
                    Config.getInstance().getSettings().ignoredModels = ignoredModels;
                    refresh();
                }
            } catch (IOException e) {
                Dialogs.showError(getScene(), "Couldn't import ignore list", e.getLocalizedMessage(), e);
            }
        }
    }
}
