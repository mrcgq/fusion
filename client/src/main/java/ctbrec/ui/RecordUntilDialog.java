package ctbrec.ui;

import static ctbrec.SubsequentAction.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import ctbrec.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Model;
import ctbrec.SubsequentAction;
import ctbrec.ui.controls.DateTimePicker;
import ctbrec.ui.controls.Dialogs;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

public class RecordUntilDialog {

    private static final Logger LOG = LoggerFactory.getLogger(RecordUntilDialog.class);

    private final Node source;
    private final Model model;
    private final GridPane gridPane = new GridPane();

    private RadioButton pauseButton;
    private RadioButton removeButton;
    private DateTimePicker datePicker;

    private Config config = Config.getInstance();

    public RecordUntilDialog(Node source, Model model) {
        this.source = source;
        this.model = model;
        createGui();
    }

    private void createGui() {
        source.setCursor(Cursor.WAIT);
        datePicker = new DateTimePicker();
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20, 150, 10, 10));
        gridPane.add(new Label("Stop at"), 0, 0);
        gridPane.add(datePicker, 1, 0);
        Label l = new Label("And then");
        l.setPadding(new Insets(5, 0, 0, 0));
        gridPane.add(l, 0, 1);
        GridPane.setValignment(l, VPos.TOP);
        var toggleGroup = new ToggleGroup();
        pauseButton = new RadioButton("pause recording");
        pauseButton.setSelected(model.getRecordUntilSubsequentAction() == PAUSE);
        pauseButton.setToggleGroup(toggleGroup);
        removeButton = new RadioButton("remove model");
        removeButton.setSelected(model.getRecordUntilSubsequentAction() == REMOVE);
        removeButton.setToggleGroup(toggleGroup);
        RadioButton recordLaterButton = new RadioButton("mark for later");
        recordLaterButton.setSelected(model.getRecordUntilSubsequentAction() == RECORD_LATER);
        recordLaterButton.setToggleGroup(toggleGroup);
        var row = new VBox();
        row.getChildren().addAll(pauseButton, removeButton, recordLaterButton);
        VBox.setMargin(pauseButton, new Insets(5));
        VBox.setMargin(removeButton, new Insets(5));
        VBox.setMargin(recordLaterButton, new Insets(5));
        gridPane.add(row, 1, 1);
        if (model.isRecordingTimeLimited()) {
            var localDate = LocalDateTime.ofInstant(model.getRecordUntil(), ZoneId.systemDefault());
            datePicker.setDateTimeValue(localDate);
        } else {
            var localDate = LocalDateTime.now().plusMinutes(config.getSettings().recordUntilDefaultDurationInMinutes);
            datePicker.setDateTimeValue(localDate);
        }
    }

    public boolean showAndWait() {
        boolean confirmed = Dialogs.showCustomInput(source.getScene(), "Stop Recording of " + model.getDisplayName() + " at", gridPane);
        if (confirmed) {
            SubsequentAction action = getSubsequentAction();
            LOG.info("Stop at {} and {}", datePicker.getDateTimeValue(), action);
            var stopAt = Instant.from(datePicker.getDateTimeValue().atZone(ZoneId.systemDefault()));
            model.setRecordUntil(stopAt);
            model.setRecordUntilSubsequentAction(action);
        }
        return confirmed;
    }

    private SubsequentAction getSubsequentAction() {
        if (pauseButton.isSelected()) {
            return PAUSE;
        } else if (removeButton.isSelected()) {
            return REMOVE;
        } else {
            return RECORD_LATER;
        }
    }
}
