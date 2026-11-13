package ctbrec.ui.sites.amateurtv;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.sites.amateurtv.AmateurTvModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static ctbrec.io.HttpConstants.*;

public class AmateurTvUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(AmateurTvUpdateService.class);
    private static final int ITEMS_PER_PAGE = 48;

    private AmateurTv site;
    private String url;
    private boolean requiresLogin = false;
    private List<Model> modelsList;
    private Instant lastListInfoRequest = Instant.EPOCH;

    public AmateurTvUpdateService(AmateurTv site, String url) {
        this.site = site;
        this.url = url;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                if (requiresLogin) {
                    if (!SiteUiFactory.getUi(site).login()) {
                        throw new IOException("- Login is required");
                    }
                    ;
                }
                return getModelList().stream()
                        .skip((page - 1) * (long) ITEMS_PER_PAGE)
                        .limit(ITEMS_PER_PAGE)
                        .collect(Collectors.toList()); // NOSONAR
            }
        };
    }

    private List<Model> getModelList() throws IOException {
        if (Duration.between(lastListInfoRequest, Instant.now()).getSeconds() < 30) {
            return modelsList;
        }
        lastListInfoRequest = Instant.now();
        modelsList = loadModelList();
        if (modelsList == null) {
            modelsList = Collections.emptyList();
        }
        return modelsList;
    }

    private List<Model> loadModelList() throws IOException {
        LOG.debug("Fetching page {}", url);
        Request request = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT, Locale.ENGLISH.getLanguage())
                .header(REFERER, site.getBaseUrl() + "/following")
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                String content = response.body().string();
                List<Model> models = new ArrayList<>();
                JSONObject json = new JSONObject(content);
                if (json.has("body")) {
                    JSONObject body = json.getJSONObject("body");
                    if (body.has("cams")) {
                        JSONArray cams = body.getJSONArray("cams");
                        parseModels(cams, models);
                    }
                    if (body.has("list") && body.has("total")) {
                        if (body.optInt("total") > 0) {
                            JSONArray list = body.getJSONArray("list");
                            parseModels(list, models);
                        }
                    }
                }
                if (json.has("cams")) {
                    JSONArray cams = json.getJSONArray("cams");
                    parseModels(cams, models);
                }
                return models;
            } else {
                int code = response.code();
                throw new IOException("HTTP status " + code);
            }
        }
    }

    private void parseModels(JSONArray jsonModels, List<Model> models) {
        for (var i = 0; i < jsonModels.length(); i++) {
            JSONObject m = jsonModels.getJSONObject(i);
            String name = m.optString("username");
            AmateurTvModel model = (AmateurTvModel) site.createModel(name);
            if (m.optBoolean("capturesEnabled", true) && m.has("capture")) {
                model.setPreview(m.optString("capture"));
            } else {
                model.setPreview(site.getBaseUrl() + m.optString("avatar"));
            }
            model.setDescription(m.optString("topic"));
            models.add(model);
        }
    }

    public void requiresLogin(boolean requiresLogin) {
        this.requiresLogin = requiresLogin;
    }
}
