package ctbrec.ui.tabs.recorded;

import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.SaveFileSelectionBox;
import ctbrec.ui.tabs.recorded.ModelImportExport.ExportIncludes;
import ctbrec.ui.tabs.recorded.ModelImportExport.ExportOptions;
import javafx.geometry.Insets;
import javafx.geometry.VPos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

import static ctbrec.ui.tabs.recorded.ModelImportExport.ExportIncludes.*;

public class ModelExportDialog {

    private final Node source;
    private final GridPane gridPane = new GridPane();

    private CheckBox notesButton;
    private CheckBox groupsButton;
    private CheckBox portraisButton;
    private SaveFileSelectionBox fileSelectionBox;

    public ModelExportDialog(Node source) {
        this.source = source;
        createGui();
    }

    private void createGui() {
        source.setCursor(Cursor.WAIT);
        gridPane.setHgap(10);
        gridPane.setVgap(10);
        gridPane.setPadding(new Insets(20, 150, 10, 10));
        Label l = new Label("Export to file");
        gridPane.add(l, 0, 0);
        fileSelectionBox = new SaveFileSelectionBox();
        gridPane.add(fileSelectionBox, 1, 0);
        GridPane.setValignment(l, VPos.TOP);
        GridPane.setMargin(l, new Insets(5, 0, 0, 0));
        GridPane.setHgrow(fileSelectionBox, Priority.ALWAYS);
        notesButton = new CheckBox("notes");
        notesButton.setSelected(true);
        groupsButton = new CheckBox("groups");
        groupsButton.setSelected(true);
        portraisButton = new CheckBox("portraits");
        portraisButton.setSelected(true);
        var row = new VBox();
        row.getChildren().addAll(notesButton, groupsButton, portraisButton);
        VBox.setMargin(notesButton, new Insets(5));
        VBox.setMargin(groupsButton, new Insets(5));
        VBox.setMargin(portraisButton, new Insets(5));
        l = new Label("Include");
        GridPane.setMargin(l, new Insets(5, 0, 0, 0));
        GridPane.setValignment(l, VPos.TOP);
        gridPane.add(l, 0, 1);
        gridPane.add(row, 1, 1);
    }

    public ExportOptions showAndWait() {
        try {
            boolean confirmed = Dialogs.showCustomInput(source.getScene(), "Export model list", gridPane);
            if (confirmed) {
                Set<ExportIncludes> exportIncludes = new HashSet<>();
                if (notesButton.isSelected()) {
                    exportIncludes.add(NOTES);
                }
                if (groupsButton.isSelected()) {
                    exportIncludes.add(GROUPS);
                }
                if (portraisButton.isSelected()) {
                    exportIncludes.add(PORTRAITS);
                }
                return new ExportOptions(exportIncludes, new File(fileSelectionBox.fileProperty().getValue()));
            }
            return null;
        } finally {
            source.setCursor(Cursor.DEFAULT);
        }
    }
}
