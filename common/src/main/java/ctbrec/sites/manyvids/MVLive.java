package ctbrec.sites.manyvids;

import ctbrec.Model;
import ctbrec.Model.State;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class MVLive extends AbstractSite {

    private static final String STARS = "stars";

    public static final String WS_ORIGIN = "https://live.manyvids.com";
    public static final String BASE_URL = "https://www.manyvids.com/";

    private MVLiveHttpClient httpClient;
    private String mvtoken;

    @Override
    public String getName() {
        return "MV Live";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public String getAffiliateLink() {
        return getBaseUrl() + "/Join-MV/1002294529";
    }

    @Override
    public MVLiveModel createModel(String name) {
        MVLiveModel model = new MVLiveModel();
        model.setName(name);
        model.setDescription("");
        model.setSite(this);
        model.setUrl(WS_ORIGIN + '/' + name);
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
    public boolean login() throws IOException {
        return false;
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new MVLiveHttpClient(getConfig());
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
    public boolean isSiteForModel(Model m) {
        return m instanceof MVLiveModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return false;
    }

    @Override
    public void init() {
        // nothing to do
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    String getMvToken() throws IOException {
        if (mvtoken == null) {
            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                    .build();
            try (Response response = getHttpClient().execute(request)) {
                if (response.isSuccessful()) {
                    Element tag = HtmlParser.getTag(response.body().string(), "html");
                    mvtoken = tag.attr("data-mvtoken");
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
        return mvtoken;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        List<Model> result = new ArrayList<>();
        RequestBody body = new FormBody.Builder()
                .add("mvtoken", getMvToken())
                .add("type", "search")
                .add("category", STARS)
                .add("search", q)
                .build();
        Request request = new Request.Builder()
                .url("https://www.manyvids.com/includes/filterSearch.php")
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .header(ORIGIN, MVLive.BASE_URL)
                .header(REFERER, MVLive.BASE_URL)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .post(body)
                .build();
        try (Response response = getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                String responseBody = response.body().string();
                parseSearchResult(result, responseBody);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
        return result;
    }

    private void parseSearchResult(List<Model> result, String responseBody) {
        JSONObject json = new JSONObject(responseBody);
        if (json.has(STARS)) {
            JSONArray stars = json.getJSONArray(STARS);
            for (int i = 0; i < stars.length(); i++) {
                JSONObject star = stars.getJSONObject(i);
                String name = star.getString("label");
                MVLiveModel model = createModel(name);
                long id = star.getLong("id");
                String url = BASE_URL + "/Profile/" + id + '/' + model.getDisplayName().replace(" ", "-") + '/';
                model.setUrl(url);
                model.setPreview(star.getString("img"));
                if (star.optString("is_live").equals("1")) {
                    if (star.optString("is_private").equals("1")) {
                        model.setOnlineState(State.PRIVATE);
                    } else {
                        model.setOnlineState(State.ONLINE);
                    }
                } else {
                    model.setOnlineState(State.OFFLINE);
                }
                result.add(model);
            }
        }
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https://live.manyvids.com/(?:stream/)?(.*?)(?:/.*?)?").matcher(url.trim());
        if (m.matches()) {
            String modelName = URLDecoder.decode(m.group(1), UTF_8);
            return createModel(modelName);
        }
        m = Pattern.compile("https://www.manyvids.com/MVLive/(.*?)/\\d+/?").matcher(url.trim());
        if (m.matches()) {
            String modelName = URLDecoder.decode(m.group(1), UTF_8);
            return createModel(modelName);
        }

        return super.createModelFromUrl(url);
    }
}
