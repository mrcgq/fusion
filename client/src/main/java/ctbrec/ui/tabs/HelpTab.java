package ctbrec.ui.tabs;

import ctbrec.docs.DocServer;
import ctbrec.ui.DesktopIntegration;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Tab;
import javafx.scene.layout.BorderPane;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class HelpTab extends Tab {

    public HelpTab() {
        setClosable(true);
        setText("Help");

        var openHelp = new Button("Open Help");
        openHelp.setPadding(new Insets(20));
        var layout = new BorderPane(openHelp);
        BorderPane.setMargin(openHelp, new Insets(20));
        setContent(layout);
        AtomicBoolean started = new AtomicBoolean(false);
        openHelp.setOnAction(e -> {
                    synchronized (started) {
                        if (!started.get()) {
                            new Thread(() -> {
                                try {
                                    DocServer.start(() ->
                                            Platform.runLater(() -> {
                                                started.set(true);
                                                DesktopIntegration.open("http://localhost:5689/docs/index.md");
                                            })
                                    );
                                } catch (Exception ex) {
                                    log.error("Couldn't start documentation server", ex);
                                }
                            }).start();
                        } else {
                            DesktopIntegration.open("http://localhost:5689/docs/index.md");
                        }
                    }
                }
        );
    }
}
