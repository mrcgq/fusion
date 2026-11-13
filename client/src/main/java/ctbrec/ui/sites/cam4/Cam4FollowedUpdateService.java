package ctbrec.ui.sites.cam4;

import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.cam4.Cam4;
import ctbrec.sites.cam4.Cam4Model;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Cam4FollowedUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(Cam4FollowedUpdateService.class);

    private final Cam4 site;
    private boolean showOnline = true;

    public Cam4FollowedUpdateService(Cam4 site) {
        this.site = site;
        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName("ThumbOverviewTab UpdateService");
            return t;
        });
        setExecutor(executor);
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                return loadModelList();
            }
        };
    }

    private List<Model> loadModelList() throws IOException {
        if (!site.credentialsAvailable()) {
            return Collections.emptyList();
        }

        // login first
        SiteUiFactory.getUi(site).login();
        String url = site.getBaseUrl() + "/directoryCams?directoryJson=true&online=" + showOnline + "&url=true&friends=true&favorites=true&resultsPerPage=90";
        LOG.debug("Fetching page {}", url);
        Request req = new Request.Builder().url(url).build();
        try (var response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                var content = response.body().string();
                return parseModels(content);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private List<Model> parseModels(String content) {
        List<Model> models = new ArrayList<>();
        var json = new JSONObject(content);
        var users = json.getJSONArray("users");
        for (var i = 0; i < users.length(); i++) {
            var modelJson = users.getJSONObject(i);
            var username = modelJson.optString("username");
            Cam4Model model = site.createModel(username);
            model.setPreview(modelJson.optString("snapshotImageLink"));
            model.setOnlineStateByShowType(modelJson.optString("showType"));
            model.setDescription(modelJson.optString("statusMessage"));
            if (modelJson.has("hlsPreviewUrl")) {
                model.setPlaylistUrl(modelJson.getString("hlsPreviewUrl"));
            }
            models.add(model);
        }
        return models;
    }

    public void setShowOnline(boolean online) {
        this.showOnline = online;
    }
}
