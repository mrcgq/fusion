package ctbrec.sites.winktv;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;

public class WinkTv extends AbstractSite {

    public static String domain = "www.winktv.co.kr";
    public static String baseUri = "https://www.winktv.co.kr";
    private HttpClient httpClient;

    @Override
    public void init() throws IOException {
    }

    @Override
    public String getName() {
        return "WinkTv";
    }

    @Override
    public String getBaseUrl() {
        return baseUri;
    }

    @Override
    public String getAffiliateLink() {
        return baseUri;
    }

    @Override
    public String getBuyTokensLink() {
        return getAffiliateLink();
    }

    @Override
    public WinkTvModel createModel(String name) {
        WinkTvModel model = new WinkTvModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + "/live/play/" + name);
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        return 0d;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new WinkTvHttpClient(getConfig());
        }
        return httpClient;
    }

    @Override
    public void shutdown() {
        if (httpClient != null) {
            httpClient.shutdown();
        }
    }

    @Override
    public boolean supportsTips() {
        return false;
    }

    @Override
    public boolean supportsFollow() {
        return false;
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public boolean searchRequiresLogin() {
        return false;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        if (StringUtil.isBlank(q)) {
            return Collections.emptyList();
        }
        String url = "https://api.winktv.co.kr/v1/live";
        FormBody body = new FormBody.Builder()
                .add("offset", "0")
                .add("limit", "30")
                .add("orderBy", "user")
                .add("searchVal", URLEncoder.encode(q, "utf-8"))
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, getBaseUrl() + "/")
                .header(ORIGIN, getBaseUrl())
                .post(body)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.optBoolean("result") && json.has("list")) {
                    List<Model> models = new ArrayList<>();
                    JSONArray results = json.getJSONArray("list");
                    if (results.length() == 0) {
                        return Collections.emptyList();
                    }
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        WinkTvModel model = createModel(result.optString("userId"));
                        model.setPreview(result.optString("thumbUrl"));
                        models.add(model);
                    }
                    return models;
                } else {
                    return Collections.emptyList();
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof WinkTvModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return false;
    }

    @Override
    public Model createModelFromUrl(String url) {
        String[] patterns = {
                "https://.*?winktv.co.kr/live/play/([_a-zA-Z0-9]+)",
                "https://.*?winktv.co.kr/channel/([_a-zA-Z0-9]+)",
                "https://.*?pandalive.co.kr/live/play/([_a-zA-Z0-9]+)",
                "https://.*?pandalive.co.kr/channel/([_a-zA-Z0-9]+)"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(url);
            if (m.matches()) {
                String modelName = m.group(1);
                return createModel(modelName);
            }
        }
        return super.createModelFromUrl(url);
    }
}
