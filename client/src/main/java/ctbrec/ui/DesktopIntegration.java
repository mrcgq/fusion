package ctbrec.ui;

import ctbrec.Config;
import ctbrec.OS;
import ctbrec.StringUtil;
import ctbrec.io.StreamRedirector;
import ctbrec.recorder.Recorder;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.TrayIcon.MessageType;
import java.io.File;
import java.io.IOException;
import java.net.URI;

public class DesktopIntegration {

    private DesktopIntegration() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(DesktopIntegration.class);

    private static Recorder recorder;
    private static Stage primaryStage;
    private static TrayIcon trayIcon;

    public static void open(String uri) {
        Config cfg = Config.getInstance();
        Runtime rt = Runtime.getRuntime();
        String[] cmdline = createCmdline(uri);

        if (!cfg.getSettings().browserOverride.isEmpty()) {
            try {
                rt.exec(cmdline);
                return;
            } catch (Exception e) {
                LOG.debug("Couldn't open URL with user-defined {} {}", cmdline, uri);
            }
        }

        if (!cfg.getSettings().forceBrowserOverride) {
            try {
                CamrecApplication.hostServices.showDocument(uri);
                return;
            } catch (Exception e) {
                LOG.debug("Couldn't open URL with host services {}", uri);
            }

            // opening with HostServices failed, now try Desktop
            try {
                Desktop.getDesktop().browse(new URI(uri));
                return;
            } catch (Exception e) {
                LOG.debug("Couldn't open URL with Desktop {}", uri);
            }

            // try external helpers
            var externalHelpers = new String[]{"kde-open5", "kde-open", "gnome-open", "xdg-open"};
            for (String helper : externalHelpers) {
                try {
                    rt.exec(helper + " " + uri);
                    return;
                } catch (IOException e) {
                    LOG.debug("Couldn't open URL with {} {}", helper, uri);
                }
            }
        }

        // all attempts failed, show a dialog with URL at least
        Alert info = new AutosizeAlert(Alert.AlertType.ERROR);
        info.setTitle("Open URL");
        info.setContentText("Couldn't open URL");
        var pane = new BorderPane();
        pane.setTop(new Label());
        var urlField = new TextField(uri);
        urlField.setPadding(new Insets(10));
        urlField.setEditable(false);
        pane.setCenter(urlField);
        info.getDialogPane().setExpandableContent(pane);
        info.getDialogPane().setExpanded(true);
        info.show();
    }

    private static String[] createCmdline(String streamUrl) {
        Config cfg = Config.getInstance();
        String params = cfg.getSettings().browserParams.trim();

        String[] cmdline;
        if (params.isEmpty()) {
            cmdline = new String[2];
        } else {
            String[] playerArgs = StringUtil.splitParams(params);
            cmdline = new String[playerArgs.length + 2];
            System.arraycopy(playerArgs, 0, cmdline, 1, playerArgs.length);
        }
        cmdline[0] = cfg.getSettings().browserOverride;
        cmdline[cmdline.length - 1] = streamUrl;
        return cmdline;
    }

    public static void open(File f) {
        try {
            Desktop.getDesktop().open(f);
            return;
        } catch (Exception e) {
            LOG.debug("Couldn't open file with Desktop {}", f);
        }

        // try external helpers
        var externalHelpers = new String[]{"kde-open5", "kde-open", "gnome-open", "xdg-open"};
        var rt = Runtime.getRuntime();
        for (String helper : externalHelpers) {
            try {
                rt.exec(helper + " " + f.getAbsolutePath());
                return;
            } catch (IOException e) {
                LOG.debug("Couldn't open file with {} {}", helper, f);
            }
        }

        // all attempts failed, show a dialog with path at least
        Alert info = new AutosizeAlert(Alert.AlertType.ERROR);
        info.setTitle("Open file");
        info.setContentText("Couldn't open file");
        var pane = new BorderPane();
        pane.setTop(new Label());
        var urlField = new TextField(f.toString());
        urlField.setPadding(new Insets(10));
        urlField.setEditable(false);
        pane.setCenter(urlField);
        info.getDialogPane().setExpandableContent(pane);
        info.getDialogPane().setExpanded(true);
        info.show();
    }

    public static void notification(String title, String header, String msg) {
        if (OS.getOsType() == OS.TYPE.LINUX) {
            notifyLinux(title, header, msg);
        } else if (OS.getOsType() == OS.TYPE.WINDOWS) {
            notifyWindows(title, header, msg);
        } else if (OS.getOsType() == OS.TYPE.MAC) {
            notifyMac(title, header, msg);
        } else {
            // unknown system, try systemtray notification anyways
            notifySystemTray(title, header, msg);
        }
    }

    private static void notifyLinux(String title, String header, String msg) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "notify-send",
                    "-u", "normal",
                    "-t", "5000",
                    "-a", title,
                    header,
                    msg.replace("-", "\\\\-").replace("\\s", "\\\\ "),
                    "--icon=dialog-information"
            });
            new Thread(new StreamRedirector(p.getInputStream(), System.out)).start(); // NOSONAR
            new Thread(new StreamRedirector(p.getErrorStream(), System.err)).start(); // NOSONAR
        } catch (NullPointerException e) {
            // can happen at start, ignore
        } catch (IOException e1) {
            LOG.error("Notification failed", e1);
        }
    }

    private static void notifyWindows(String title, String header, String msg) {
        notifySystemTray(title, header, msg);
    }

    private static void notifyMac(String title, String header, String msg) {
        notifySystemTray(title, header, msg);
    }

    private static synchronized void notifySystemTray(String title, String header, String msg) {
        if (SystemTray.isSupported()) {
            createTrayIcon(primaryStage);
            trayIcon.displayMessage(header, msg, MessageType.INFO);
        } else {
            LOG.error("SystemTray notifications not supported by this OS");
        }
    }

    public static void minimizeToTray(Stage primaryStage) {
        Platform.setImplicitExit(false);
        boolean supported = createTrayIcon(primaryStage);
        if (supported) {
            primaryStage.hide();
        }
    }

    private static boolean createTrayIcon(Stage stage) {
        if (SystemTray.isSupported()) {
            boolean created = true;
            if (trayIcon == null) {
                trayIcon = new ctbrec.ui.TrayIcon(stage, recorder);
                created = trayIcon.createTrayIcon();
            }
            return created;
        } else {
            LOG.error("SystemTray notifications not supported by this OS");
            return false;
        }
    }

    public static void setRecorder(Recorder recorder) {
        DesktopIntegration.recorder = recorder;
    }

    public static void setPrimaryStage(Stage primaryStage) {
        DesktopIntegration.primaryStage = primaryStage;
    }

    public static void updateTrayIcon(int activeRecordings) {
        if (trayIcon != null) {
            trayIcon.updateActiveRecordings(activeRecordings);
        }
    }
}
