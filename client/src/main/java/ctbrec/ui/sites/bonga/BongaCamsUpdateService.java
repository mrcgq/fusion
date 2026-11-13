package ctbrec.ui.sites.bonga;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.sites.bonga.BongaCams;
import ctbrec.sites.bonga.BongaCamsModel;
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
import java.util.List;
import java.util.Locale;

import static ctbrec.io.HttpConstants.*;

public class BongaCamsUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(BongaCamsUpdateService.class);

    private final BongaCams bongaCams;
    private final String url;
    private final boolean requiresLogin;

    public BongaCamsUpdateService(BongaCams bongaCams, String url) {
        this(bongaCams, url, false);
    }

    public BongaCamsUpdateService(BongaCams bongaCams, String url, boolean requiresLogin) {
        this.bongaCams = bongaCams;
        this.url = url;
        this.requiresLogin = requiresLogin;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (requiresLogin) {
                    SiteUiFactory.getUi(bongaCams).login();
                }
                return loadModelList();
            }

        };
    }

    private List<Model> loadModelList() throws IOException {
        String pageUrl = url + ((page - 1) * 72);
        LOG.debug("Fetching page {}", pageUrl);
        var request = new Request.Builder()
                .url(pageUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT, Locale.ENGLISH.getLanguage())
                .header(REFERER, bongaCams.getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (var response = bongaCams.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                var content = response.body().string();
                List<Model> models = new ArrayList<>();
                var json = new JSONObject(content);
                if (json.optString("status").equals("success")) {
                    var jsonModels = json.getJSONArray("models");
                    parseModels(jsonModels, models);
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
            var m = jsonModels.getJSONObject(i);
            var name = m.optString("username");
            if (StringUtil.isBlank(name)) {
                continue;
            }
            BongaCamsModel model = (BongaCamsModel) bongaCams.createModel(name);
            model.mapOnlineState(m.optString("room"));
            model.setOnline(m.optInt("viewers") > 0);
            model.setPreview("https://en.bongacams.com/images/default/thumb_m_female.png");
            if (m.has("thumb_image")) {
                String thumb = m.optString("thumb_image");
                if (StringUtil.isNotBlank(thumb)) {
                    model.setPreview("https:" + thumb.replace("{ext}", "jpg"));
                }
            }
            if (m.has("display_name")) {
                model.setDisplayName(m.getString("display_name"));
            }
            model.setDescription(m.optString("topic"));
            models.add(model);
        }
    }
}
