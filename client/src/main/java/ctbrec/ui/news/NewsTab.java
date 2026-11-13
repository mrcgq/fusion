package ctbrec.ui.news;

import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Version;
import ctbrec.io.HttpException;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tab;
import javafx.scene.layout.VBox;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Objects;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.USER_AGENT;

@Slf4j
public class NewsTab extends Tab implements TabSelectionListener {
    private static final String URL = "https://git.ctbrec.com/api/v1/repos/j62/ctbrec/releases";
    private final Config config;
    private final HostServices hostServices;
    private final VBox layout = new VBox();
    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();
    private final OkHttpClient httpClient = new OkHttpClient();

    public NewsTab(Config config, HostServices hostServices) {
        this.config = config;
        this.hostServices = hostServices;
        setText("Releases");
        layout.setMaxWidth(800);
        layout.setAlignment(Pos.CENTER);
        setContent(new ScrollPane(layout));
    }

    @Override
    public void selected() {
        GlobalThreadPool.submit(this::loadReleases);
    }

    private void loadReleases() {
        try {
            var request = new Request.Builder()
                    .url(URL)
                    .header(USER_AGENT, "ctbrec " + Version.getVersion())
                    .build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    var body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                    log.debug(body);
                    if (body.startsWith("[")) {
                        onSuccess(body);
                    } else {
                        throw new IOException("Unexpected response: " + body);
                    }
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        } catch (IOException e) {
            log.info("Error while loading releases", e);
            Dialogs.showError(getTabPane().getScene(), "Releases", "Couldn't load release information", e);
        }
    }

    private void onSuccess(String body) throws IOException {
        JSONArray releases = new JSONArray(body);
        Platform.runLater(() -> {
            layout.getChildren().clear();
            for (int i = 0; i < releases.length(); i++) {
                JSONObject release = releases.getJSONObject(i);
                String tagName = release.optString("tag_name", "Unknown Version");
                String releaseName = release.optString("name", "No Name");
                String description = release.optString("body", "No description available.");
                JSONArray assets = release.optJSONArray("assets");

                var releasePane = new VBox();
                releasePane.setPadding(new Insets(10));
                releasePane.setSpacing(5);

                Label versionLabel = new Label("Version: " + tagName);
                versionLabel.setStyle("-fx-font-weight: bold;");

                Label nameLabel = new Label("Release: " + releaseName);
                Label descLabel = new Label(description);

                releasePane.getChildren().addAll(versionLabel, nameLabel, descLabel);

                if (assets != null) {
                    Label assetsLabel = new Label("Assets:");
                    releasePane.getChildren().add(assetsLabel);
                    for (int j = 0; j < assets.length(); j++) {
                        JSONObject asset = assets.getJSONObject(j);
                        String assetName = asset.optString("name", "Unknown File");
                        String downloadUrl = asset.optString("browser_download_url", "#");
                        int size = asset.optInt("size", 0);
                        int downloads = asset.optInt("download_count", 0);

                        Hyperlink assetLink = new Hyperlink(assetName + " (" + size / 1024 / 1024 + " MB, " + downloads + " downloads)");
                        assetLink.setOnAction(event -> hostServices.showDocument(downloadUrl));

                        releasePane.getChildren().add(assetLink);
                    }
                }

                layout.getChildren().add(releasePane);
                VBox.setMargin(releasePane, new Insets(10));
            }
        });
    }

    @Override
    public void deselected() {
        // nothing to do
    }
}
