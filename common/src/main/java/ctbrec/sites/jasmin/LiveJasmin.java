package ctbrec.sites.jasmin;

import ctbrec.Model;
import ctbrec.NotLoggedInExcetion;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;

public class LiveJasmin extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasmin.class);
    public static String baseUrl = "https://www.livejasmin.com";
    public static String baseDomain = "www.livejasmin.com";
    private HttpClient httpClient;

    @Override
    public String getName() {
        return "LiveJasmin";
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getAffiliateLink() {
        return "https://awejmp.com/?siteId=jasmin&categoryName=girl&pageName=listpage&performerName=&prm[psid]=0xb00bface&prm[pstool]=205_1&prm[psprogram]=revs&prm[campaign_id]=&subAffId={SUBAFFID}&filters=";
    }

    @Override
    public Model createModel(String name) {
        LiveJasminModel model = new LiveJasminModel();
        model.setName(name);
        model.setDescription("");
        model.setSite(this);
        model.setUrl(getBaseUrl() + "/en/chat/" + name);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        if (getLiveJasminHttpClient().login()) {
            String sessionId = getLiveJasminHttpClient().getSessionId();
            String url = getBaseUrl() + "/en/offline-surprise/get-member-balance?session=" + sessionId;
            Request request = new Request.Builder().url(url)
                    .addHeader(USER_AGENT, getConfig().getSettings().httpUserAgent)
                    .addHeader(ACCEPT, "*/*")
                    .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .addHeader(REFERER, getBaseUrl())
                    .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                    .build();
            try (Response response = getHttpClient().execute(request)) {
                if (response.isSuccessful()) {
                    String body = Objects.requireNonNull(response.body()).string();
                    JSONObject json = new JSONObject(body);
                    if (json.optBoolean("success")) {
                        return json.optDouble("result");
                    } else {
                        throw new IOException("Response was not successful: " + url + "\n" + body);
                    }
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        } else {
            throw new IOException(new NotLoggedInExcetion());
        }
    }

    @Override
    public String getBuyTokensLink() {
        return getAffiliateLink();
    }

    @Override
    public boolean login() throws IOException {
        return getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new LiveJasminHttpClient(getConfig());
        }
        return httpClient;
    }

    @Override
    public void init() throws IOException {
        baseUrl = getConfig().getSettings().livejasminBaseUrl;
        HttpUrl url = HttpUrl.parse(baseUrl);
        baseDomain = url.topPrivateDomain();
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
        String sessionId = getLiveJasminHttpClient().getSessionId();
        String pathSegment = sessionId.charAt(0) == 'm' ? "member" : "guest";
        String base = "https://api-gateway.dditsadn.com";
        String url = base + "/v2/" + pathSegment + "/auto-suggest/suggestions?"
                + "product=livejasmin"
                + "&session=" + sessionId
                + "&limit=5"
                + "&embed[]=data.performers.*"
                + "&version=v2"
                + "&criteria[]=searchText,%3D," + URLEncoder.encode(q, "utf-8")
                + "&criteria[]=language,%3D,en"
                + "&criteria[]=gender,%3D,female";
        Request request = new Request.Builder().url(url)
                .addHeader(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .addHeader(ACCEPT, "*/*")
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        LOG.debug("Search URL: {}", url);
        try (Response response = getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body()).string();
                JSONObject json = new JSONObject(body);
                List<Model> models = new ArrayList<>();
                JSONObject data = json.getJSONObject("data");
                JSONArray performers = data.getJSONArray("performers");
                for (int i = 0; i < performers.length(); i++) {
                    JSONObject performer = performers.getJSONObject(i);
                    LiveJasminModel model = (LiveJasminModel) createModel(performer.getString("performerNick"));
                    model.setDisplayName(performer.optString("displayName", model.getName()));
                    model.setId(String.valueOf(performer.getLong("performerId")));
                    JSONArray profilePics = performer.optJSONArray("profilePictures");
                    if (profilePics != null && profilePics.length() > 0) {
                        JSONObject profilePic = profilePics.getJSONObject(profilePics.length() - 1);
                        model.setPreview("https:" + profilePic.getString("url"));
                    }
                    models.add(model);
                }
                return models;
            } else {
                throw new HttpException(response.code(), Objects.requireNonNull(response.body()).string());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof LiveJasminModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return !getConfig().getSettings().livejasminUsername.isEmpty();
    }

    private LiveJasminHttpClient getLiveJasminHttpClient() {
        return (LiveJasminHttpClient) getHttpClient();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("http.*?livejasmin\\.com.*?#!chat/(.*)").matcher(url);
        if (m.find()) {
            String name = m.group(1);
            return createModel(name);
        }
        m = Pattern.compile("http.*?livejasmin\\.com.*?/chat(?:-html5)?/(.*)").matcher(url);
        if (m.find()) {
            String name = m.group(1);
            return createModel(name);
        }

        return super.createModelFromUrl(url);
    }

    public boolean login(boolean renew) throws IOException {
        return getLiveJasminHttpClient().login(renew);
    }
}
