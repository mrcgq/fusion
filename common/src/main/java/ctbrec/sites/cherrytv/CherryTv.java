package ctbrec.sites.cherrytv;

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
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CherryTv extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(CherryTv.class);

    public static final String BASE_URL = "https://cherry.tv";

    private CherryTvHttpClient httpClient;

    @Override
    public String getName() {
        return "CherryTV";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public String getAffiliateLink() {
        return getBaseUrl();
    }

    @Override
    public CherryTvModel createModel(String name) {
        CherryTvModel model = new CherryTvModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + '/' + name);
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
        return getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new CherryTvHttpClient(getConfig());
        }
        return httpClient;
    }

    @Override
    public void init() throws IOException {
        // nothing to do
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
        String url = "https://api.cherry.tv/graphql?operationName=Search&variables="
                + "{\"limit\":6,\"slug\":\"" + URLEncoder.encode(q, UTF_8) + "\"}&extensions={\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"b6b001b46111c5634b5d3f48caa5ad38e747d74a5c841f447f0094e7f2bc2fb1\"}}";
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        LOG.debug("Search URL: {}", req.url());
        List<Model> result = new LinkedList<>();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(Objects.requireNonNull(response.body()).string());
                LOG.trace(json.toString(2));
                JSONObject data = json.getJSONObject("data");
                JSONArray streamers = data.getJSONArray("search");
                for (int i = 0; i < streamers.length(); i++) {
                    JSONObject hit = streamers.getJSONObject(i);
                    CherryTvModel model = createModel(hit.getString("id"));
                    model.setId(hit.getString("id"));
                    boolean online = hit.optString("broadcastStatus").equalsIgnoreCase("Live");
                    model.setOnline(online);
                    model.setOnlineState(online ? ONLINE : OFFLINE);
                    model.setDescription(hit.getString("description"));
                    model.setPreview(hit.getString("image"));
                    result.add(model);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
        return result;
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof CherryTvModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().cherryTvUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://.*?cherry\\.tv/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            CherryTvModel model = createModel(modelName);
            try {
                model.isOnline(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOG.warn("Couldn't determine model id. This could cause problems in the future", e);
            }
            return model;
        } else {
            return super.createModelFromUrl(url);
        }
    }
}
