package ctbrec.sites.xlovecam;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.Model;
import okhttp3.FormBody;
import okhttp3.FormBody.Builder;
import okhttp3.Request;
import okhttp3.Response;

public class XloveCamModelLoader {

    private static final Logger LOG = LoggerFactory.getLogger(XloveCamModelLoader.class);
    private static final int ITEMS_PER_PAGE = 35;
    private static final int CAM_RANK = 35;

    private XloveCam site;

    public XloveCamModelLoader(XloveCam xloveCam) {
        this.site = xloveCam;
    }

    public List<Model> loadModelList(int page, Map<String, String> filterOptions) throws IOException {
        String pageUrl = "https://mobile.xlovecam.com/en/performerAction/onlineList/?x-req=" + ITEMS_PER_PAGE + "&x-off-s=" + ((page - 1) * ITEMS_PER_PAGE);
        LOG.debug("Fetching page {}", pageUrl);
        Builder form = new FormBody.Builder()
                .add("config[sort][id]", Integer.toString(CAM_RANK))
                .add("offset[from]", Integer.toString((page - 1) * ITEMS_PER_PAGE))
                .add("offset[length]", Integer.toString(ITEMS_PER_PAGE));
        for (Entry<String, String> entry : filterOptions.entrySet()) {
            form.add(entry.getKey(), entry.getValue());
        }

        Request request = new Request.Builder()
                .url(pageUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT, Locale.ENGLISH.getLanguage())
                .header(REFERER, site.getBaseUrl())
                .header(ORIGIN, site.getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .post(form.build())
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                List<Model> models = new ArrayList<>();
                JSONObject json = new JSONObject(body);
                if(json.has("content")) {
                    parseModels(json, models);
                }
                return models;
            } else {
                int code = response.code();
                throw new IOException("HTTP status " + code);
            }
        }
    }

    private void parseModels(JSONObject json, List<Model> models) {
        JSONObject content = json.getJSONObject("content");
        if (content.has("performerList")) {
            JSONArray performers = content.getJSONArray("performerList");
            for (int i = 0; i < performers.length(); i++) {
                JSONObject performer = performers.getJSONObject(i);
                XloveCamModel model = site.createModel(performer.getString("nickname"));
                model.setPreview("https:" + performer.optString("liveImg"));
                models.add(model);
            }
        }
    }
}
