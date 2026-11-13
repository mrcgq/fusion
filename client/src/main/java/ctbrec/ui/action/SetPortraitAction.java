package ctbrec.ui.action;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.ui.CamrecApplication;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.controls.FileSelectionBox;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;

public class SetPortraitAction extends AbstractPortraitAction {
    private static final Logger LOG = LoggerFactory.getLogger(SetPortraitAction.class);

    private final Consumer<Model> callback;

    public SetPortraitAction(Node source, Model selectedModel, Consumer<Model> callback) {
        this.source = source;
        this.model = selectedModel;
        this.callback = callback;
    }

    public void execute() {
        source.setCursor(Cursor.WAIT);
        GridPane pane = new GridPane();
        Label l = new Label("Select a portrait image. Leave empty to remove a portrait again.");
        pane.add(l, 0, 0);
        FileSelectionBox portraitSelectionBox = new FileSelectionBox();
        pane.add(portraitSelectionBox, 0, 1);
        GridPane.setMargin(l, new Insets(5));
        GridPane.setMargin(portraitSelectionBox, new Insets(5));
        boolean accepted = Dialogs.showCustomInput(source.getScene(), "Select a portrait image", pane);
        if (!accepted) {
            source.setCursor(Cursor.DEFAULT);
            return;
        }
        String selectedFile = portraitSelectionBox.fileProperty().getValue();

        if (StringUtil.isBlank(selectedFile)) {
            removePortrait(model.getUrl());
        } else {
            LOG.debug("User selected {}", selectedFile);
            boolean success = processImageFile(selectedFile);
            if (success) {
                try {
                    firePortraitChanged();
                    runCallback();
                } catch (Exception e) {
                    Dialogs.showError("Set Portrait", "Couldn't change portrait image: ", e);
                }
            }
        }
        source.setCursor(Cursor.DEFAULT);
    }

    private void removePortrait(String modelUrl) {
        try {
            CamrecApplication.portraitStore.removePortrait(modelUrl);
            firePortraitChanged();
            runCallback();
        } catch (IOException e) {
            Dialogs.showError("Remove Portrait", "Couldn't remove portrait image: ", e);
        }
    }

    private void runCallback() {
        if (callback != null) {
            try {
                callback.accept(model);
            } catch (Exception e) {
                LOG.error("Error while executing callback", e);
            }
        }
    }

    private boolean processImageFile(String selectedFile) {
        try {
            BufferedImage original = ImageIO.read(new File(selectedFile));
            BufferedImage croppedImage = cropImage(original);
            BufferedImage portrait = convertToScaledJpg(croppedImage);
            boolean success = store(model.getUrl(), portrait);
            if (!success) {
                LOG.debug("Available formats: {}", Arrays.toString(ImageIO.getWriterFormatNames()));
                throw new IOException("No suitable writer found for image format " + FORMAT);
            }
            return success;
        } catch (IOException e) {
            LOG.error("Error while changing portrait image", e);
            Dialogs.showError("Set Portrait", "Couldn't change portrait image: ", e);
            return false;
        }
    }
}
