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
public class CacheSettingsPane extends HBox {

    private ComboBox<String> cacheSizeCombo;
    private final SettingsTab settingsTab;
    private final Config config;
    private static final List<String> names = List.of("disabled", "16 MiB", "64 MiB", "128 MiB", "256 MiB", "512 MiB");
    private static final List<Integer> values = List.of(0, 16, 64, 128, 256, 512);

    public CacheSettingsPane(SettingsTab settingsTab, Config config) {
        this.settingsTab = settingsTab;
        this.config = config;
        setSpacing(5);
        getChildren().addAll(buildCacheSizeCombo());
    }

    private ComboBox<String> buildCacheSizeCombo() {
        ObservableList<String> lst = FXCollections.observableList(names);
        cacheSizeCombo = new ComboBox<>(lst);
        cacheSizeCombo.setOnAction(evt -> saveCacheConfig());
        int size = config.getSettings().thumbCacheSize;
        int selectedIndex = values.indexOf(size);
        if (selectedIndex < 0) {
            selectedIndex = 1;
        }
        cacheSizeCombo.getSelectionModel().select(selectedIndex);
        return cacheSizeCombo;
    }

    private void saveCacheConfig() {
        int index = cacheSizeCombo.getSelectionModel().getSelectedIndex();
        int size = values.get(index);
        config.getSettings().thumbCacheSize = size;
        try {
            config.save();
            settingsTab.showRestartRequired();
        } catch (IOException e) {
            log.error("Can't save config", e);
        }
    }
}
