package ctbrec.ui.settings;

import static ctbrec.recorder.postprocessing.CreateContactSheet.*;
import static java.lang.Boolean.*;

import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.ui.settings.api.Category;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.Setting;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.ColorPicker;
import javafx.scene.paint.Color;

public class CreateContactSheetPaneFactory extends AbstractPostProcessingPaneFactory {

    private SimpleStringProperty background;

    @Override
    public Preferences doCreatePostProcessorPane(PostProcessor pp) {
        var totalSize = new SimpleStringProperty(null, TOTAL_SIZE, pp.getConfig().getOrDefault(TOTAL_SIZE, "1920"));
        var padding = new SimpleStringProperty(null, PADDING, pp.getConfig().getOrDefault(PADDING, "4"));
        var cols = new SimpleStringProperty(null, COLS, pp.getConfig().getOrDefault(COLS, "8"));
        var rows = new SimpleStringProperty(null, ROWS, pp.getConfig().getOrDefault(ROWS, "7"));
        var filename = new SimpleStringProperty(null, FILENAME, pp.getConfig().getOrDefault(FILENAME, "contactsheet.jpg"));
        background = new SimpleStringProperty(null, BACKGROUND, pp.getConfig().getOrDefault(BACKGROUND, "0x333333"));
        var burnTimestamp = new SimpleBooleanProperty(null, BURN_IN_TIMESTAMP,
                Boolean.valueOf(pp.getConfig().getOrDefault(BURN_IN_TIMESTAMP, TRUE.toString())));
        properties.add(totalSize);
        properties.add(padding);
        properties.add(cols);
        properties.add(rows);
        properties.add(filename);
        properties.add(background);
        properties.add(burnTimestamp);

        var backgroundSetting = Setting.of("", background, "Hexadecimal value of the background color for the space between the thumbnails");
        var prefs = Preferences.of(new MapPreferencesStorage(),
                Category.of(pp.getName(),
                        Setting.of("Total Width", totalSize, "Total width of the generated contact sheet"),
                        Setting.of("Padding", padding, "Padding between the thumbnails"),
                        Setting.of("Columns", cols ),
                        Setting.of("Rows", rows),
                        Setting.of("File Name", filename),
                        Setting.of("Background", createColorPicker(background.get())),
                        Setting.of("Timestamp (experimental)", burnTimestamp, "Burn in a timestamp on each thumb. Can be very slow on some systems."),
                        backgroundSetting
                        )
                );

        try {
            // hide the background color input field, because we use a color picker instead
            backgroundSetting.getGui().setVisible(false);
        } catch (Exception e) {
            // hiding the background color input field didn't work, that's ok
        }

        return prefs;
    }

    private ColorPicker createColorPicker(String hexColor) {
        var preselection = Color.web(hexColor);
        var colorPicker = new ColorPicker(preselection);
        colorPicker.setOnAction(e -> background.set(colorPicker.getValue().toString()));
        return colorPicker;
    }
}
