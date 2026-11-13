package ctbrec.sites.cam4;

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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpClient.bodyToJsonArray;
import static ctbrec.io.HttpConstants.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Cam4 extends AbstractSite {

    public static final String BASE_URI = "https://www.cam4.com";
    public static final String AFFILIATE_LINK = BASE_URI + "/?referrerId=1514a80d87b5effb456cca02f6743aa1";

    private HttpClient httpClient;

    @Override
    public String getName() {
        return "Cam4";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URI;
    }

    @Override
    public String getAffiliateLink() {
        return AFFILIATE_LINK;
    }

    @Override
    public Cam4Model createModel(String name) {
        Cam4Model m = new Cam4Model();
        m.setSite(this);
        m.setDisplayName(name);
        m.setName(name.toLowerCase());
        m.setUrl(getBaseUrl() + '/' + m.getName() + '/');
        return m;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        if (!credentialsAvailable()) {
            throw new IOException("Not logged in");
        }
        return ((Cam4HttpClient) getHttpClient()).getTokenBalance();
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
            httpClient = new Cam4HttpClient(getConfig());
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
    public void init() throws IOException {
        // noop
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
        return true;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        List<Model> result = new ArrayList<>();
        if (credentialsAvailable()) {
            search(q, false, result);
            search(q, true, result);
        }
        return result;
    }

    private void search(String q, boolean offline, List<Model> models) throws IOException {
        String url = BASE_URI + "/usernameSearch?username=" + URLEncoder.encode(q, UTF_8);
        if (offline) {
            url += "&offline=true";
        }
        Request req = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = bodyToJsonArray(response);
                JSONArray results = new JSONArray(body);
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    Model model = createModel(result.getString("username"));
                    String thumb;
                    if (result.has("thumbnailId")) {
                        thumb = "https://snapshots.xcdnpro.com/thumbnails/" + model.getName() + "?s=" + result.getString("thumbnailId");
                    } else {
                        thumb = result.getString("profileImageLink");
                    }
                    if (StringUtil.isNotBlank(thumb)) {
                        model.setPreview(thumb);
                    }
                    models.add(model);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof Cam4Model;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().cam4Username;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://(?:www\\.)?cam4.*?.com/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }
}
