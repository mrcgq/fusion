package ctbrec.sites.streamate;

import ctbrec.Model;
import ctbrec.StringUtil;
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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.ORIGIN;

public class Streamate extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(Streamate.class);

    public static final String API_URL = "https://streamate.com";
    public static final String BASE_URL = "https://www.streamate.com";
    public static final String NAIAD_URL = "https://member.naiadsystems.com/search/v3";

    private StreamateHttpClient httpClient;

    @Override
    public String getName() {
        return "Streamate";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public String getAffiliateLink() {
        return BASE_URL + "/landing/click/?AFNO=2-11329.1";
    }

    @Override
    public Model createModel(String name) {
        StreamateModel model = new StreamateModel();
        model.setName(name);
        model.setUrl(BASE_URL + "/cam/" + name);
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
    public StreamateHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new StreamateHttpClient(getConfig());
        }
        return httpClient;
    }

    @Override
    public void init() throws IOException {
        // nothing to initialize
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
    public boolean searchRequiresLogin() {
        return false;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        String url = NAIAD_URL + "/autocomplete?filters=gender:&performerCount=10&domain=streamate.com&tagCount=5&query=" + URLEncoder.encode(q, "utf-8");
        LOG.debug("Search: {}", url);
        String saKey = getHttpClient().getSaKey();
        String smtid = UUID.randomUUID() + "G0211569057409";
        Request req = getHttpClient().newRequestBuilder()
                .url(url)
                .header(ORIGIN, getBaseUrl())
                .header("sakey", saKey)
                .header("platform", "SCP")
                .header("smtid", smtid)
                .header("smeid", smtid)
                .header("smvid", smtid)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body(), "HTTP body is null").string();
                JSONObject json = new JSONObject(body);
                if (json.has("performers")) {
                    List<Model> models = new ArrayList<>();
                    JSONArray performers = json.getJSONArray("performers");
                    for (int i = 0; i < performers.length(); i++) {
                        JSONObject result = performers.getJSONObject(i);
                        StreamateModel model = (StreamateModel) createModel(result.getString("nickname"));
                        model.setId(result.getLong("id"));
                        String thumb = result.getString("thumbnail");
                        if (thumb != null) {
                            model.setPreview(thumb);
                        }
                        model.setOnline(result.optBoolean("online"));
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
        return m instanceof StreamateModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().streamateUsername;
        return StringUtil.isNotBlank(username);
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://.*?streamate.com/cam/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }
}
