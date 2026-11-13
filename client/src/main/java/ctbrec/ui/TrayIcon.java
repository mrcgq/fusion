package ctbrec.ui;

import ctbrec.Config;
import ctbrec.event.EventBusHolder;
import ctbrec.recorder.Recorder;
import ctbrec.ui.controls.Dialogs;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static java.awt.Font.BOLD;
import static java.awt.RenderingHints.KEY_ANTIALIASING;
import static java.awt.RenderingHints.VALUE_ANTIALIAS_ON;

public class TrayIcon {

    private static final Logger LOG = LoggerFactory.getLogger(TrayIcon.class);

    private final Stage stage;
    private final Recorder recorder;
    private SystemTray tray;
    private java.awt.TrayIcon awtTrayIcon;
    private BufferedImage background;

    public TrayIcon(Stage stage, Recorder recorder) {
        this.stage = stage;
        this.recorder = recorder;
    }

    boolean createTrayIcon() {
        if (SystemTray.isSupported()) {
            if (tray == null) {
                String title = CamrecApplication.title;
                tray = SystemTray.getSystemTray();
                BufferedImage image = null;
                try {
                    image = createImage(recorder.getCurrentlyRecording().size());
                } catch (Exception e) {
                    // fail silently
                }

                PopupMenu menu = createTrayContextMenu(stage);
                awtTrayIcon = new java.awt.TrayIcon(image, title, menu);
                awtTrayIcon.setImageAutoSize(true);
                awtTrayIcon.setToolTip(title);
                try {
                    tray.add(awtTrayIcon);
                } catch (AWTException e) {
                    LOG.error("Couldn't add tray icon", e);
                }
                awtTrayIcon.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent e) {
                        if (SwingUtilities.isLeftMouseButton(e)) {
                            toggleVisibility(stage);
                        }
                    }
                });
            }
            return true;
        } else {
            LOG.error("SystemTray notifications not supported by this OS");
            return false;
        }
    }

    private PopupMenu createTrayContextMenu(Stage stage) {
        var menu = new PopupMenu();
        var show = new MenuItem("Show");
        show.addActionListener(evt -> restoreStage(stage));
        menu.add(show);
        menu.addSeparator();
        var pauseRecording = new MenuItem("Pause recording");
        pauseRecording.addActionListener(evt -> {
            try {
                recorder.pause();
            } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                Dialogs.showError(stage.getScene(), "Pausing recording", "Pausing of the recorder failed", e);
            }
        });
        menu.add(pauseRecording);
        var resumeRecording = new MenuItem("Resume recording");
        resumeRecording.addActionListener(evt -> {
            try {
                recorder.resume();
            } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                Dialogs.showError(stage.getScene(), "Resuming recording", "Resuming of the recorder failed", e);
            }
        });
        menu.add(resumeRecording);
        menu.addSeparator();
        var exit = new MenuItem("Exit");
        exit.addActionListener(evt -> exit());
        menu.add(exit);
        return menu;
    }

    private void restoreStage(Stage stage) {
        Platform.runLater(() -> {
            stage.setX(Config.getInstance().getSettings().windowX);
            stage.setY(Config.getInstance().getSettings().windowY);
            LOG.debug("Restoring window location {},{}", stage.getX(), stage.getY());
            stage.setIconified(false);
            stage.show();
            stage.toFront();
            EventBusHolder.BUS.post(Map.of("event", "stage_restored"));
        });
    }

    private void toggleVisibility(Stage stage) {
        if (stage.isShowing()) {
            Platform.setImplicitExit(false);
            Platform.runLater(stage::hide);
        } else {
            restoreStage(stage);
        }
    }

    private void exit() {
        EventBusHolder.BUS.post(Map.of("event", "shutdown"));
    }

    public void displayMessage(String header, String msg, java.awt.TrayIcon.MessageType info) {
        createTrayIcon();
        awtTrayIcon.displayMessage(header, msg, info);
    }

    public void updateActiveRecordings(int activeRecordings) {
        try {
            createTrayIcon();
            if (awtTrayIcon != null) {
                awtTrayIcon.setImage(createImage(activeRecordings));
            }
        } catch (IOException e) {
            LOG.error("Couldn't update tray icon image", e);
        }
    }

    private BufferedImage createImage(int number) throws IOException {
        if (this.background == null) {
            this.background = ImageIO.read(TrayIcon.class.getResource("/icon64.png"));
        }

        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = (Graphics2D) image.getGraphics();
        g2.drawImage(background, 0, 0, (img, infoflags, x, y, width, height) -> false);
        if (number > 0 && Config.getInstance().getSettings().showActiveRecordingsInTray) {
            g2.setColor(Color.decode("#dc4444"));
            g2.fillOval(0, 0, 64, 64);

            String text = String.valueOf(number);
            String fontFamily = Config.getInstance().getSettings().showActiveRecordingsInTrayFont;
            int fontSize = Config.getInstance().getSettings().showActiveRecordingsInTrayFontSize;
            g2.setRenderingHint(KEY_ANTIALIASING, VALUE_ANTIALIAS_ON);
            Font font = new Font(fontFamily, BOLD, fontSize);
            g2.setFont(font);
            FontMetrics fontMetrics = g2.getFontMetrics(font);
            LineMetrics lineMetrics = fontMetrics.getLineMetrics(text, g2);
            Rectangle2D stringBounds = fontMetrics.getStringBounds(text, g2);
            g2.setColor(Color.decode(Config.getInstance().getSettings().showActiveRecordingsInTrayColor));
            int x = (int) (image.getWidth() - stringBounds.getWidth()) / 2;
            int y = (int) (((image.getHeight() - lineMetrics.getAscent()) / 2) - 8 - stringBounds.getY());
            g2.drawString(text, x, y);
            g2.dispose();
        }
        return image;
    }
}
