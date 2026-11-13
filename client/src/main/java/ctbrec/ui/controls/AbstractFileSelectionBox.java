package ctbrec.ui.controls;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.StringUtil;
import ctbrec.ui.AutosizeAlert;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;

public abstract class AbstractFileSelectionBox extends HBox {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractFileSelectionBox.class);

    private final StringProperty fileProperty = new SimpleStringProperty();
    private final Tooltip validationError = new Tooltip();
    protected TextField fileInput;
    protected boolean allowEmptyValue = false;
    protected boolean saveDialog = false;
    protected boolean validationDisabled = false;

    protected AbstractFileSelectionBox() {
        super(5);
        fileInput = new TextField();
        fileInput.textProperty().addListener(textListener());
        fileInput.focusedProperty().addListener((obs, o, n) -> {
            if (Objects.equals(Boolean.FALSE, n)) {
                validationError.hide();
            }
        });
        var browse = createBrowseButton();
        browse.disableProperty().bind(disableProperty());
        browse.prefHeightProperty().bind(fileInput.prefWidthProperty());
        fileInput.disableProperty().bind(disableProperty());
        fileInput.textProperty().bindBidirectional(fileProperty);
        getChildren().addAll(fileInput, browse);
        HBox.setHgrow(fileInput, Priority.ALWAYS);

        disabledProperty().addListener((obs, oldV, newV) -> {
            if (Objects.equals(Boolean.TRUE, newV)) {
                hideValidationHints();
            } else {
                if (StringUtil.isNotBlank(fileInput.getText())) {
                    setFile(new File(fileInput.getText()));
                }
            }
        });
    }

    protected AbstractFileSelectionBox(String initialValue) {
        this();
        fileInput.setText(initialValue);
    }

    protected ChangeListener<String> textListener() {
        return (obs, o, n) -> {
            var input = fileInput.getText();
            if (StringUtil.isBlank(input)) {
                if (allowEmptyValue) {
                    fileProperty.set("");
                    hideValidationHints();
                }
            } else {
                var program = new File(input);
                setFile(program);
            }
        };
    }

    protected void setFile(File file) {
        var msg = validate(file);
        if (msg != null) {
            fileInput.setBorder(new Border(new BorderStroke(Color.RED, BorderStrokeStyle.DASHED, new CornerRadii(2), new BorderWidths(2))));
            validationError.setText(msg);
            fileInput.setTooltip(validationError);
            var p = fileInput.localToScreen(fileInput.getTranslateY(), fileInput.getTranslateY());
            if (!validationError.isShowing() && getScene() != null) {
                validationError.show(getScene().getWindow(), p.getX(), p.getY() + fileInput.getHeight() + 4);
            }
        } else {
            fileProperty.set(file.getAbsolutePath());
            hideValidationHints();
        }
    }

    private void hideValidationHints() {
        fileInput.setBorder(Border.EMPTY);
        fileInput.setTooltip(null);
        validationError.hide();
    }

    protected String validate(File file) {
        if (isDisabled() || validationDisabled) {
            return null;
        }

        if (file == null || !file.exists()) {
            return "File does not exist";
        } else {
            return null;
        }
    }

    public void allowEmptyValue() {
        this.allowEmptyValue = true;
    }

    public void useSaveDialog() {
        this.saveDialog = true;
    }

    public void disableValidation() {
        validationDisabled = true;
    }

    private Button createBrowseButton() {
        var button = new Button("Select");
        button.setOnAction(e -> choose());
        button.prefHeightProperty().bind(this.heightProperty());
        button.prefWidthProperty().set(70);
        return button;
    }

    protected void choose() {
        var chooser = new FileChooser();
        File program;
        if (saveDialog) {
            program = chooser.showSaveDialog(null);
        } else {
            program = chooser.showOpenDialog(null);
        }
        if (program != null) {
            try {
                fileInput.setText(program.getCanonicalPath());
            } catch (IOException e1) {
                LOG.error("Couldn't determine path", e1);
                var alert = new AutosizeAlert(Alert.AlertType.ERROR, getScene());
                alert.setTitle("Whoopsie");
                alert.setContentText("Couldn't determine path");
                alert.showAndWait();
            }
            setFile(program);
        }
    }

    public StringProperty fileProperty() {
        return fileProperty;
    }
}
