package ctbrec.ui.action;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.event.EventBusHolder;
import ctbrec.ui.CamrecApplication;
import javafx.scene.Node;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

public abstract class AbstractPortraitAction {
    public static final String FORMAT = "jpg";

    protected Node source;
    protected Model model;

    protected BufferedImage convertToScaledJpg(BufferedImage original) {
        java.awt.Image scaledPortrait = original.getScaledInstance(-1, 256, java.awt.Image.SCALE_SMOOTH);
        BufferedImage bimage = new BufferedImage(scaledPortrait.getWidth(null), scaledPortrait.getHeight(null), BufferedImage.TYPE_INT_RGB);
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(scaledPortrait, 0, 0, null);
        bGr.dispose();
        return bimage;
    }

    protected boolean store(String modelUrl, BufferedImage portrait) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(portrait, FORMAT, bytes);
        CamrecApplication.portraitStore.writePortrait(modelUrl, bytes.toByteArray());
        return true;
    }

    protected File getPortraitFile(String portraitId) {
        File configDir = Config.getInstance().getConfigDir();
        File portraitDir = new File(configDir, "portraits");
        File output = new File(portraitDir, portraitId + '.' + FORMAT);
        return output;
    }

    protected BufferedImage cropImage(BufferedImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w > h) {
            return cropSides(img);
        } else {
            return cropTopAndBottom(img);
        }
    }

    protected BufferedImage cropSides(BufferedImage img) {
        int overlap = img.getWidth() - img.getHeight();
        return img.getSubimage(overlap / 2, 0, img.getHeight(), img.getHeight());
    }

    protected BufferedImage cropTopAndBottom(BufferedImage img) {
        int overlap = img.getHeight() - img.getWidth();
        return img.getSubimage(0, overlap / 2, img.getWidth(), img.getWidth());
    }

    protected void firePortraitChanged() {
        EventBusHolder.BUS.post(new PortraitChangedEvent(model));
    }

    public static class PortraitChangedEvent {
        private final Model mdl;

        public PortraitChangedEvent(Model model) {
            this.mdl = model;
        }

        public Model getModel() {
            return mdl;
        }
    }
}
