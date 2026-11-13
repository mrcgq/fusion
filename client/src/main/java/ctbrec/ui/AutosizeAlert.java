package ctbrec.ui;

import java.io.InputStream;

import ctbrec.ui.controls.Dialogs;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.scene.layout.Region;
import javafx.stage.Stage;

public class AutosizeAlert extends Alert {

    private Scene parent;

    public AutosizeAlert(AlertType type) {
        super(type, null);
    }

    public AutosizeAlert(AlertType type, Scene parent) {
        super(type);
        this.parent = parent;
        init();
    }

    public AutosizeAlert(AlertType type, String text, Scene parent, ButtonType... buttons) {
        super(type, text, buttons);
        this.parent = parent;
        init();
    }

    private void init() {
        setResizable(true);
        getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        if(parent != null) {
            var stage = (Stage) getDialogPane().getScene().getWindow();
            stage.getScene().getStylesheets().addAll(parent.getStylesheets());
            InputStream icon = Dialogs.class.getResourceAsStream("/icon.png");
            stage.getIcons().add(new Image(icon));
        }
    }
}
