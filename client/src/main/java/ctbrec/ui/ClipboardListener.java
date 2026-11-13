package ctbrec.ui;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import javafx.application.Platform;
import javafx.scene.input.Clipboard;

public class ClipboardListener implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(ClipboardListener.class);
    private Recorder recorder;
    private List<Site> sites;
    private Clipboard systemClipboard;
    private String lastUrl = null;

    public ClipboardListener(Recorder recorder, List<Site> sites) {
        this.recorder = recorder;
        this.sites = sites;
        systemClipboard = Clipboard.getSystemClipboard();
    }

    @Override
    public void run() {
        Platform.runLater(() -> {
            try {
                String url = null;
                if (systemClipboard.hasUrl()) {
                    url = systemClipboard.getUrl();
                } else if (systemClipboard.hasString()) {
                    url = systemClipboard.getString();
                }
                if (url != null && !Objects.equals(url, lastUrl)) {
                    lastUrl = url;
                    addModelIfUrlMatches(url);
                }
            } catch (Exception e) {
                LOG.error("Error in clipboard polling loop", e);
            }
        });
    }

    private void addModelIfUrlMatches(String url) {
        for (Site site : sites) {
            var m = site.createModelFromUrl(url);
            if (m != null) {
                try {
                    recorder.addModel(m);
                    DesktopIntegration.notification("Add from clipboard", "Model added", "Model " + m.getDisplayName() + " added");
                } catch (InvalidKeyException | NoSuchAlgorithmException | IOException e) {
                    DesktopIntegration.notification("Add from clipboard", "Error", "Couldn't add URL from clipboard: " + e.getLocalizedMessage());
                }
                break;
            }
        }
    }
}
