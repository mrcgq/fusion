package ctbrec.sites.bonga;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.*;

public class BongaCams extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(BongaCams.class);

    private String baseUrl = "https://bongacams.com";

    private BongaCamsHttpClient httpClient;

    @Override
    public void init() throws IOException {
        baseUrl = getConfig().getSettings().bongacamsBaseUrl;
    }

    @Override
    public String getName() {
        return "BongaCams";
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getAffiliateLink() {
        return "https://bongacams10.com/track?c=610249";
    }

    @Override
    public Model createModel(String name) {
        BongaCamsModel model = new BongaCamsModel();
        model.setName(name);
        model.setUrl(baseUrl + '/' + name);
        model.setDescription("");
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        int userId = ((BongaCamsHttpClient) getHttpClient()).getUserId();
        String url = getBaseUrl() + "/tools/amf.php";
        RequestBody body = new FormBody.Builder()
                .add("method", "ping")
                .add("args[]", Integer.toString(userId))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .post(body)
                .build();
        try (Response response = getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                if (json.optString("status").equals("online")) {
                    JSONObject userData = json.getJSONObject("userData");
                    return (double) userData.getInt("balance");
                } else {
                    throw new IOException("Request was not successful: " + json.toString(2));
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
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
            httpClient = new BongaCamsHttpClient(this, getConfig());
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
        if (StringUtil.isBlank(q)) {
            return Collections.emptyList();
        }
        String url = getBaseUrl() + "/tools/listing_v3.php?livetab=all&_suggest=1&model_search[display_name][text]=" + URLEncoder.encode(q, "utf-8");
        Request req = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .addHeader("Accept", "*/*")
                .addHeader("Accept-Encoding", "deflate")
                .addHeader("Accept-Language", "en,en-US;q=0.9")
                .addHeader(REFERER, getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                JSONObject json = new JSONObject(body);
                if (json.has("models")) {
                    List<Model> models = new ArrayList<>();
                    parseModelList(models, json);
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

    private void parseModelList(List<Model> models, JSONObject json) {
        JSONArray results = json.getJSONArray("models");
        int maxResults = Math.min(30, results.length());
        for (int i = 0; i < maxResults; i++) {
            JSONObject result = results.getJSONObject(i);
            if (result.has("username")) {
                String username = result.getString("username");
                Model model = createModel(username.toLowerCase());
                if (result.has("avatar")) {
                    model.setPreview("https://i.bcicdn.com" + result.getString("avatar"));
                }
                if (result.has("name")) {
                    model.setDisplayName(result.getString("name"));
                }
                models.add(model);
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof BongaCamsModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().bongaUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://.*?bongacams.com(?:/profile)?/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }
}
