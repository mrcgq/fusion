package ctbrec.ui.sites.cam4;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.sites.cam4.Cam4;
import ctbrec.sites.cam4.Cam4Model;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.ACCEPT_LANGUAGE;
import static ctbrec.io.HttpConstants.USER_AGENT;

public class Cam4UpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(Cam4UpdateService.class);
    private String url;
    private final Cam4 site;
    private final boolean loginRequired;

    public Cam4UpdateService(String url, boolean loginRequired, Cam4 site) {
        this.site = site;
        this.url = url;
        this.loginRequired = loginRequired;

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
                if (loginRequired && StringUtil.isBlank(Config.getInstance().getSettings().cam4Username)) {
                    return Collections.emptyList();
                } else {
                    String pageUrl = Cam4UpdateService.this.url + "&page=" + page;
                    LOG.debug("Fetching page {}", pageUrl);
                    if (loginRequired) {
                        SiteUiFactory.getUi(site).login();
                    }
                    var request = new Request.Builder()
                            .url(pageUrl)
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .build();
                    try (var response = site.getHttpClient().execute(request)) {
                        if (response.isSuccessful()) {
                            return parseModels(response.body().string());
                        } else {
                            throw new HttpException(response.code(), response.message());
                        }
                    }
                }
            }

        };
    }

    private List<Model> parseModels(String body) {
        var json = new JSONObject(body);
        JSONArray users = json.getJSONArray("users");
        List<Model> models = new ArrayList<>(users.length());
        for (int i = 0; i < users.length(); i++) {
            JSONObject user = users.getJSONObject(i);
            Cam4Model model = site.createModel(user.getString("username"));
            model.setPlaylistUrl(user.getString("hlsPreviewUrl"));
            model.setPreview(user.getString("snapshotImageLink"));
            model.setDescription(user.getString("statusMessage"));
            model.setOnlineState(ONLINE);
            models.add(model);
        }
        return models;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
