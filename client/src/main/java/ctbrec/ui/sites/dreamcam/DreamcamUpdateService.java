package ctbrec.ui.sites.dreamcam;

import static ctbrec.io.HttpConstants.*;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.sites.dreamcam.DreamcamModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javafx.concurrent.Task;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DreamcamUpdateService extends PaginatedScheduledService {
    private static final Logger LOG = LoggerFactory.getLogger(DreamcamUpdateService.class);
    private static final String API_URL = "https://api.dreamcam.co.kr/v1/live";
    private static final int modelsPerPage = 64;
    private Dreamcam site;
    private String url;

    public DreamcamUpdateService(Dreamcam site, String url) {
        this.site = site;
        this.url = url;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                return loadModelList();
            }
        };
    }

    private List<Model> loadModelList() throws IOException {
        int offset = (getPage() - 1) * modelsPerPage;
        int limit = modelsPerPage;
        String paginatedUrl = url + "&offset=" + offset + "&limit=" + limit;
        LOG.debug("Fetching page {}", paginatedUrl);
        Request req = new Request.Builder()
                .url(paginatedUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, site.getBaseUrl() + "/")
                .header(ORIGIN, site.getBaseUrl())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                List<Model> models = new ArrayList<>();
                String content = response.body().string();
                JSONObject json = new JSONObject(content);
                if (json.has("pageItems")) {
                    JSONArray modelNodes = json.getJSONArray("pageItems");
                    parseModels(modelNodes, models);
                }
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void parseModels(JSONArray jsonModels, List<Model> models) {
        for (int i = 0; i < jsonModels.length(); i++) {
            JSONObject m = jsonModels.getJSONObject(i);
            String name = m.optString("modelNickname");
            DreamcamModel model = (DreamcamModel) site.createModel(name);
            model.setDisplayName(name);
            model.setPreview(m.optString("modelProfilePhotoUrl"));
            model.setDescription(m.optString("broadcastTextStatus"));
            models.add(model);
        }
    }
}
