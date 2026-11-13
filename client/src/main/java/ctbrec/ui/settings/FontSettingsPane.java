package ctbrec.ui.settings;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.ui.controls.Dialogs;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.text.Font;

public class FontSettingsPane extends HBox {

    private static final Logger LOG = LoggerFactory.getLogger(FontSettingsPane.class);

    private ComboBox<String> fontFaceCombo;
    private ComboBox<Integer> fontSizeCombo;

    private SettingsTab settingsTab;
    private Config config;

    public FontSettingsPane(SettingsTab settingsTab, Config config) {
        this.settingsTab = settingsTab;
        this.config = config;
        setSpacing(5);
        getChildren().addAll(buildFontFaceCombo(), buildFontSizeCombo(), buildFontResetButton());
    }

    private ComboBox<String> buildFontFaceCombo() {
        ObservableList<String> lst = FXCollections.observableList(javafx.scene.text.Font.getFamilies());
        fontFaceCombo = new ComboBox<>(lst);
        fontFaceCombo.getSelectionModel().select(0);
        fontFaceCombo.setCellFactory((listview -> new ListCell<String>() {
            @Override
            protected void updateItem(String family, boolean empty) {
                super.updateItem(family, empty);
                if (empty) {
                    setText(null);
                } else {
                    setFont(Font.font(family));
                    setText(family);
                }
            }
        }));
        fontFaceCombo.getSelectionModel().select(config.getSettings().fontFamily);
        fontFaceCombo.setOnAction(evt -> saveFontConfig());
        return fontFaceCombo;
    }

    private ComboBox<Integer> buildFontSizeCombo() {
        ObservableList<Integer> lst = FXCollections
                .observableList(List.of(4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 22, 24, 26, 28, 32, 48, 64, 72, 80, 96, 128));
        fontSizeCombo = new ComboBox<>(lst);
        fontSizeCombo.setOnAction(evt -> saveFontConfig());
        int size = config.getSettings().fontSize;
        int selectedIndex = Math.max(0, lst.indexOf(size));
        fontSizeCombo.getSelectionModel().select(selectedIndex);
        return fontSizeCombo;
    }

    private void saveFontConfig() {
        Font font = Font.font(fontFaceCombo.getSelectionModel().getSelectedItem());
        int size = fontSizeCombo.getSelectionModel().getSelectedItem();
        String css = ".root {\n    -fx-font: " + size + " '" + font.getFamily() + "';\n}";
        config.getSettings().fontFamily = font.getFamily();
        config.getSettings().fontSize = size;
        try {
            config.save();
            Files.writeString(getFontCssFile().toPath(), css);
            settingsTab.showRestartRequired();
        } catch (IOException e) {
            LOG.error("Couldn't write font file", e);
            Dialogs.showError(getScene(), "Error saving configuration", "The font stylesheet file couldn't be written", e);
        }
    }

    private File getFontCssFile() {
        return new File(Config.getInstance().getConfigDir(), "font.css");
    }

    private Button buildFontResetButton() {
        var button = new Button("Reset");
        button.setOnAction(evt -> {
            try {
                Files.delete(getFontCssFile().toPath());
                settingsTab.showRestartRequired();
            } catch (IOException e) {
                LOG.error("Couldn't delete font file", e);
                Dialogs.showError(getScene(), "Error resetting font configuration", "The font stylesheet file couldn't be deleted", e);
            }
        });
        return button;
    }
}
