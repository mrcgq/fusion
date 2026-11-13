package ctbrec.ui.settings;

import java.io.IOException;

import ctbrec.Config;
import ctbrec.ui.controls.Dialogs;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;

public class ColorSettingsPane extends HBox {

    ColorPicker baseColor = new ColorPicker();
    ColorPicker accentColor = new ColorPicker();
    Button reset = new Button("Reset");
    Pane foobar = new Pane();
    private Config config;

    public ColorSettingsPane(Config config) {
        super(5);
        this.config = config;
        getChildren().add(baseColor);
        getChildren().add(accentColor);
        getChildren().add(reset);

        baseColor.setValue(Color.web(config.getSettings().colorBase));
        baseColor.setTooltip(new Tooltip("Base Color"));
        baseColor.setPrefWidth(100);
        accentColor.setValue(Color.web(config.getSettings().colorAccent));
        accentColor.setTooltip(new Tooltip("Accent Color"));
        accentColor.setPrefWidth(100);

        baseColor.setOnAction(evt -> {
            config.getSettings().colorBase = toWeb(baseColor.getValue());
            save();
        });
        accentColor.setOnAction(evt -> {
            config.getSettings().colorAccent = toWeb(accentColor.getValue());
            save();
        });
        reset.setOnAction(evt -> {
            baseColor.setValue(Color.WHITE);
            config.getSettings().colorBase = toWeb(Color.WHITE);
            accentColor.setValue(Color.WHITE);
            config.getSettings().colorAccent = toWeb(Color.WHITE);
            save();
        });
    }

    private void save() {
        try {
            config.save();
        } catch (IOException e) {
            Dialogs.showError(getScene(), "Save Settings", "Couldn't save color settings", e);
        }
    }

    private String toWeb(Color value) {
        var sb = new StringBuilder("#");
        sb.append(toHex((int) (value.getRed() * 255)));
        sb.append(toHex((int) (value.getGreen() * 255)));
        sb.append(toHex((int) (value.getBlue() * 255)));
        if(!value.isOpaque()) {
            sb.append(toHex((int) (value.getOpacity() * 255)));
        }
        return sb.toString();
    }

    private CharSequence toHex(int v) {
        var sb = new StringBuilder();
        if(v < 16) {
            sb.append('0');
        }
        sb.append(Integer.toHexString(v));
        return sb;
    }
}
