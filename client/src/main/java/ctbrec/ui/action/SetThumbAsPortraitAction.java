package ctbrec.ui.action;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.image.Image;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.image.BufferedImage;

public class SetThumbAsPortraitAction extends AbstractPortraitAction {

    private static final Logger LOG = LoggerFactory.getLogger(SetThumbAsPortraitAction.class);

    private final Image image;

    public SetThumbAsPortraitAction(Node source, Model model, Image image) {
        this.source = source;
        this.model = model;
        this.image = image;
    }

    public void execute() {
        source.setCursor(Cursor.WAIT);
        GlobalThreadPool.submit(() -> {
            try {
                BufferedImage bufferedImage = convertFxImageToAwt(image);
                BufferedImage croppedImage = cropImage(bufferedImage);
                BufferedImage portrait = convertToScaledJpg(croppedImage);
                store(model.getUrl(), portrait);
                firePortraitChanged();
            } catch (Exception e) {
                LOG.error("Error while changing portrait image", e);
                Platform.runLater(() -> Dialogs.showError("Set Portrait", "Couldn't change portrait image: ", e));
            } finally {
                Platform.runLater(() -> source.setCursor(Cursor.DEFAULT));
            }
        });
    }

    private BufferedImage convertFxImageToAwt(Image img) {
        return SwingFXUtils.fromFXImage(img, null);
    }
}
