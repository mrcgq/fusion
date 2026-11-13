package ctbrec.sites.stripchat;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Stripchat extends AbstractSite {
    private static final Random RNG = new Random();
    private static final String KEY_MODELS = "models";

    @Getter
    @Setter(AccessLevel.PROTECTED)
    private static String domain = "stripchat.com";
    @Getter
    @Setter(AccessLevel.PROTECTED)
    private static String baseUri = "https://stripchat.com";
    private HttpClient httpClient;

    @Override
    public void init() throws IOException {
        boolean hamster = getConfig().getSettings().stripchatUseXhamster;
        if (hamster) {
            Stripchat.setDomain("xhamsterlive.com");
            Stripchat.setBaseUri("https://" + domain);
        }
    }

    @Override
    public String getName() {
        return "Stripchat";
    }

    @Override
    public String getBaseUrl() {
        return baseUri;
    }

    @Override
    public String getAffiliateLink() {
        return "https://go.strpjmp.com?creativeId=1&campaignId=1&sourceId=app&path=%2F&userId=cd0bfa6152f1e2b55f8e8218de2f32c7e6c41d9846a390a2113ed4a5edfc95d8";
    }

    @Override
    public String getBuyTokensLink() {
        return getAffiliateLink();
    }

    @Override
    public StripchatModel createModel(String name) {
        StripchatModel model = new StripchatModel();
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
        String username = getConfig().getSettings().stripchatUsername;
        String url = baseUri + "/api/front/users/username/" + username;
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
            httpClient = new StripchatHttpClient(getConfig());
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
        String url = baseUri + "/api/front/v4/models/search/suggestion?query=" + URLEncoder.encode(q, UTF_8) + "&limit=15&primaryTag=girls&uniq=" + getUniq();
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, baseUri)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.has(KEY_MODELS)) {
                    List<Model> models = new ArrayList<>();
                    if (json.has(KEY_MODELS) && !json.isNull(KEY_MODELS)) {
                        JSONArray results = json.getJSONArray(KEY_MODELS);
                        for (int i = 0; i < results.length(); i++) {
                            JSONObject result = results.getJSONObject(i);
                            StripchatModel model = createModel(result.getString("username"));
                            model.setPreview(result.optString("avatarUrl"));
                            models.add(model);
                        }
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
        return m instanceof StripchatModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().stripchatUsername;
        return StringUtil.isNotBlank(username);
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://(?:.*?\\.)?(?:stripchat.com|xhamsterlive.com)/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }

    public String getUniq() {
        String dict = "0123456789abcdefghijklmnopqrstuvwxyz";
        char[] text = new char[16];
        for (int i = 0; i < 16; i++) {
            text[i] = dict.charAt(RNG.nextInt(dict.length()));
        }
        return new String(text);
    }

}
