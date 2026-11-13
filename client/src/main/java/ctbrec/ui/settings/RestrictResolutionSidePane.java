package ctbrec.ui.settings;

import ctbrec.Config;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.List;

@Slf4j
public class RestrictResolutionSidePane extends HBox {

    private ComboBox<String> combo;
    private final Config config;
    private static final List<String> names = List.of("by video height", "by shortest side");

    public RestrictResolutionSidePane(Config config) {
        this.config = config;
        setSpacing(5);
        getChildren().addAll(buildCombo());
    }

    private ComboBox<String> buildCombo() {
        ObservableList<String> lst = FXCollections.observableList(names);
        combo = new ComboBox<>(lst);
        combo.setOnAction(evt -> saveConfig());
        int index = config.getSettings().checkResolutionByMinSide ? 1 : 0;
        combo.getSelectionModel().select(index);
        return combo;
    }

    private void saveConfig() {
        int index = combo.getSelectionModel().getSelectedIndex();
        config.getSettings().checkResolutionByMinSide = (index == 1);
        try {
            config.save();
        } catch (IOException e) {
            log.error("Can't save config", e);
        }
    }
}
