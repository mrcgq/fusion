package ctbrec.sites.chaturbate;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpClient;
import ctbrec.sites.AbstractSite;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Chaturbate extends AbstractSite {

    static String baseUrl = "https://chaturbate.com";
    public static final String AFFILIATE_LINK = "https://chaturbate.com/in/?track=default&tour=grq0&campaign=97mhH";
    public static final String REGISTRATION_LINK = "https://chaturbate.com/in/?track=default&tour=g4pe&campaign=97mhH";
    private ChaturbateHttpClient httpClient;

    @Override
    public void init() throws IOException {
        baseUrl = getConfig().getSettings().chaturbateBaseUrl;
    }

    @Override
    public String getName() {
        return "Chaturbate";
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getAffiliateLink() {
        return getBaseUrl() + "/in/?track=default&tour=LQps&campaign=97mhH&room=0xb00bface";
    }

    @Override
    public Model createModel(String name) {
        String normalizedName = name.toLowerCase().trim();
        ChaturbateModel m = new ChaturbateModel(this);
        m.setName(normalizedName);
        m.setUrl(getBaseUrl() + '/' + normalizedName + '/');
        m.setPreview("https://roomimg.stream.highwebmedia.com/ri/" + normalizedName + ".jpg?" + Instant.now().getEpochSecond());
        return m;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        String username = getConfig().getSettings().chaturbateUsername;
        if (username == null || username.trim().isEmpty()) {
            throw new IOException("Not logged in");
        }

        String url = "https://chaturbate.com/p/" + username + "/";
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getHttpClient().getEffectiveUserAgent())
                .build();
        try (Response resp = getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String profilePage = resp.body().string();
                String tokenText = HtmlParser.getText(profilePage, "span.tokencount");
                int tokens = Integer.parseInt(tokenText);
                return (double) tokens;
            } else {
                throw new IOException("HTTP response: " + resp.code() + " - " + resp.message());
            }
        }
    }

    @Override
    public String getBuyTokensLink() {
        return AFFILIATE_LINK;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new ChaturbateHttpClient(getConfig());
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
    public boolean supportsFollow() {
        return true;
    }

    @Override
    public boolean supportsTips() {
        return false;
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        String url = baseUrl + "/ax/search/?keywords=" + URLEncoder.encode(q, UTF_8);
        List<Model> result = new ArrayList<>();

        // search online models
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getHttpClient().getEffectiveUserAgent())
                .header(ACCEPT, "*/*")
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, getBaseUrl())
                .build();
        try (Response resp = getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                JSONObject json = new JSONObject(resp.body().string());
                JSONArray offline = json.getJSONArray("offline");
                JSONArray online = json.getJSONArray("online");

                for (int i = 0; i < online.length(); i++) {
                    result.add(createModel(online.getString(i)));
                }
                for (int i = 0; i < offline.length(); i++) {
                    result.add(createModel(offline.getString(i)));
                }
            }
        }

        result.sort((m1, m2) -> {
            int m1match = StringUtil.percentageOfEquality(q, m1.getName());
            int m2match = StringUtil.percentageOfEquality(q, m2.getName());
            return m2match - m1match;
        });
        return result;
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof ChaturbateModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().chaturbateUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://.*?chaturbate.com(?:/p)?/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }

    @Override
    public Duration getOnlineCheckTimeout() {
        int msBetweenRequests = getConfig().getSettings().chaturbateMsBetweenRequests;
        return Duration.ofMillis(msBetweenRequests + 2000L);
    }
}
