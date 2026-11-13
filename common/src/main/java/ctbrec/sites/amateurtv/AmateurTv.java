package ctbrec.sites.amateurtv;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
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
import static java.nio.charset.StandardCharsets.UTF_8;

public class AmateurTv extends AbstractSite {

    public static final String BASE_URL = "https://en.amateur.tv";

    private AmateurTvHttpClient httpClient;


    @Override
    public void init() throws IOException {
        // nothing to do
    }

    @Override
    public String getName() {
        return "Amateur.tv";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public AmateurTvModel createModel(String name) {
        AmateurTvModel model = new AmateurTvModel();
        model.setName(name);
        model.setUrl(BASE_URL + '/' + name);
        model.setDescription("");
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        return 0d;
    }

    @Override
    public String getBuyTokensLink() {
        return getAffiliateLink();
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new AmateurTvHttpClient(getConfig());
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
        return true;
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        if (StringUtil.isBlank(q)) {
            return Collections.emptyList();
        }
        String url = getBaseUrl() + "/v3/readmodel/cache/filterbyusername/" + URLEncoder.encode(q, UTF_8);
        var req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT, Locale.ENGLISH.getLanguage())
                .header(REFERER, getBaseUrl())
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                List<Model> models = new ArrayList<>();
                JSONArray results = json.getJSONObject("cams").getJSONArray("nodes");
                int maxResults = Math.min(30, results.length());
                for (int i = 0; i < maxResults; i++) {
                    JSONObject result = results.getJSONObject(i);
                    var user = result.getJSONObject("user");
                    AmateurTvModel model = createModel(user.optString("username"));
                    model.setPreview(result.optString("imageURL"));
                    models.add(model);
                }
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof AmateurTvModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().amateurTvUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://.*?amateur.tv/(.*)").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }

    @Override
    public String getAffiliateLink() {
        return BASE_URL;
    }
}
