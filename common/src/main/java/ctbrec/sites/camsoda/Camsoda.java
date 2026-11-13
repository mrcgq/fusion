package ctbrec.sites.camsoda;

import ctbrec.Model;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Camsoda extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(Camsoda.class);
    public static final String BASE_URI = "https://www.camsoda.com";
    private HttpClient httpClient;

    @Override
    public String getName() {
        return "CamSoda";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URI;
    }

    @Override
    public String getAffiliateLink() {
        return BASE_URI;
    }

    @Override
    public String getBuyTokensLink() {
        return BASE_URI;
    }

    @Override
    public Model createModel(String name) {
        CamsodaModel model = new CamsodaModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + "/" + name);
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        if (!credentialsAvailable()) {
            throw new IOException("Account settings not available");
        }

        String url = BASE_URI + "/api/v1/user/current";
        Request request = new Request.Builder().url(url).build();
        try (Response response = getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.has("user")) {
                    JSONObject user = json.getJSONObject("user");
                    if (user.has("tokens")) {
                        return (double) user.getInt("tokens");
                    }
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
        throw new RuntimeException("Tokens not found in response");
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new CamsodaHttpClient(getConfig());
        }
        return httpClient;
    }

    @Override
    public void init() throws IOException {
        // noop
    }

    @Override
    public void shutdown() {
        if (httpClient != null) {
            httpClient.shutdown();
        }
    }

    @Override
    public boolean supportsTips() {
        return true;
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
        String url = BASE_URI + "/api/v1/browse/autocomplete?s=" + URLEncoder.encode(q, UTF_8);
        Request req = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.optBoolean("status")) {
                    List<Model> models = new ArrayList<>();
                    JSONArray results = json.getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        CamsodaModel model = (CamsodaModel) createModel(result.getString("username"));
                        String thumb = result.getString("thumb");
                        if (thumb != null) {
                            if (thumb.startsWith("//")) {
                                thumb = "https:" + thumb;
                            } else if (thumb.startsWith("/")) {
                                thumb = BASE_URI + thumb;
                            }
                            model.setPreview(thumb);
                        }
                        if (result.has("display_name")) {
                            model.setDisplayName(result.getString("display_name"));
                        }
                        models.add(model);
                    }
                    return models;
                } else {
                    LOG.warn("Search result: {}", json.toString(2));
                    return Collections.emptyList();
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof CamsodaModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().camsodaUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://(?:www\\.)?camsoda.com/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }
}
