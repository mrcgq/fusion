package ctbrec.ui.controls;

import ctbrec.ui.AutosizeAlert;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.Optional;

import static javafx.scene.control.ButtonType.*;

public class Dialogs {

    private Dialogs() {}

    private static Scene scene;

    public static void setScene(Scene scene) {
        Dialogs.scene = scene;
    }

    public static void showError(String header, String text, Throwable t) {
        showError(scene, header, text, t);
    }

    public static void showError(Scene parent, String header, String text, Throwable t) {
        Runnable r = () -> {
            Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, parent);
            alert.setTitle("Error");
            alert.setHeaderText(header);
            String content = text;
            if (t != null) {
                content += " " + t.getLocalizedMessage();
            }
            alert.setContentText(content);
            if (parent != null) {
                var stage = (Stage) alert.getDialogPane().getScene().getWindow();
                stage.getScene().getStylesheets().addAll(parent.getStylesheets());
            }
            alert.showAndWait();
        };

        if (Platform.isFxApplicationThread()) {
            r.run();
        } else {
            Platform.runLater(r);
        }
    }

    public static Optional<String> showTextInput(Scene parent, String title, String header, String text) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.getDialogPane().getButtonTypes().addAll(OK, CANCEL);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(true);
        InputStream icon = Dialogs.class.getResourceAsStream("/icon.png");
        var stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(icon));
        if (parent != null) {
            stage.getScene().getStylesheets().addAll(parent.getStylesheets());
        }

        var grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        var notes = new TextArea(text);
        notes.setPrefRowCount(3);
        grid.add(notes, 0, 0);
        dialog.getDialogPane().setContent(grid);

        Platform.runLater(notes::requestFocus);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == OK) {
                return notes.getText();
            }
            return null;
        });

        return dialog.showAndWait();
    }

    @SafeVarargs
    public static Boolean showCustomInput(Scene parent, String title, Region region, ChangeListener<Boolean> ...showingListener) {
        Dialog<?> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.getDialogPane().getButtonTypes().addAll(OK, CANCEL);
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setResizable(true);
        InputStream icon = Dialogs.class.getResourceAsStream("/icon.png");
        var stage = (Stage) dialog.getDialogPane().getScene().getWindow();
        stage.getIcons().add(new Image(icon));
        if (parent != null) {
            stage.getScene().getStylesheets().addAll(parent.getStylesheets());
        }
        dialog.getDialogPane().setContent(region);
        for (ChangeListener<Boolean> changeListener : showingListener) {
            dialog.showingProperty().addListener(changeListener);
        }
        dialog.showAndWait();
        return dialog.getResult() == OK;
    }

    public static boolean showConfirmDialog(String title, String message, String header, Scene parent) {
        var confirm = new AutosizeAlert(AlertType.CONFIRMATION, message, parent, YES, NO);
        confirm.setTitle(title);
        confirm.setHeaderText(header);
        confirm.showAndWait();
        return confirm.getResult() == YES;
    }

    public static ButtonType showShutdownDialog(Scene parent) {
        var message = "There are recordings in progress";
        var confirm = new AutosizeAlert(AlertType.CONFIRMATION, "", parent, YES, FINISH, NO);
        confirm.setTitle("Shutdown");
        confirm.setHeaderText(message);
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.YES)).setText("Shutdown Now");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.YES)).setDefaultButton(false);
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.FINISH)).setText("Shutdown Gracefully");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.FINISH)).setDefaultButton(true);
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.NO)).setText("Keep Running");
        ((Button) confirm.getDialogPane().lookupButton(ButtonType.NO)).setDefaultButton(false);
        confirm.showAndWait();
        return confirm.getResult();
    }
}
