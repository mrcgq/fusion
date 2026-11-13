package ctbrec.ui.tabs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.io.HttpException;
import ctbrec.ui.CamrecApplication;
import ctbrec.ui.CamrecApplication.Release;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.controls.Dialogs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextArea;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import okhttp3.Request;
import okhttp3.Response;

public class UpdateTab extends Tab implements TabSelectionListener {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateTab.class);

    private final TextArea changelog;

    public UpdateTab(Release latest) {
        setText("Update Available");
        var vbox = new VBox(10);
        var l = new Label("New Version available " + latest.getVersion());
        vbox.getChildren().add(l);
        VBox.setMargin(l, new Insets(20, 0, 0, 0));
        var button = new Button("Download");
        button.setOnAction(e -> DesktopIntegration.open(latest.getHtmlUrl()));
        vbox.getChildren().add(button);
        VBox.setMargin(button, new Insets(0, 0, 10, 0));
        vbox.setAlignment(Pos.CENTER);
        changelog = new TextArea();
        changelog.setEditable(false);
        changelog.setText("Loading changelog...");
        vbox.getChildren().add(changelog);
        VBox.setVgrow(changelog, Priority.ALWAYS);
        setContent(vbox);
    }

    public void loadChangeLog() {
        GlobalThreadPool.submit(() -> {
            Request req = new Request.Builder().url("https://pastebin.com/raw/fiAPtM0s").build();
            try (Response resp = CamrecApplication.httpClient.execute(req)) {
                if (resp.isSuccessful()) {
                    changelog.setText(resp.body().string());
                } else {
                    throw new HttpException(resp.code(), resp.message());
                }
            } catch (Exception e1) {
                LOG.error("Couldn't download the changelog", e1);
                Dialogs.showError(getTabPane().getScene(), "Communication error", "Couldn't download the changelog", e1);
            }
        });
    }

    @Override
    public void selected() {
        loadChangeLog();
    }

    @Override
    public void deselected() {
        // nothing to do
    }
}
