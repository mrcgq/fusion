package ctbrec.sites.secretfriends;

import ctbrec.Model;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.USER_AGENT;

public class SecretFriends extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(SecretFriends.class);
    public static final String BASE_URI = "https://www.secretfriends.com";
    private HttpClient httpClient;

    @Override
    public void init() throws IOException {
        // nothing to do
    }

    @Override
    public String getName() {
        return "SecretFriends";
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
        return getAffiliateLink();
    }

    @Override
    public SecretFriendsModel createModel(String name) {
        SecretFriendsModel model = new SecretFriendsModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + "/friends/" + name);
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        return 0d;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return false;
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new SecretFriendsHttpClient(getConfig());
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
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        String url = BASE_URI + "/user?SearchForm[keyword]=" + URLEncoder.encode(q, "utf-8");
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                List<Model> models = new ArrayList<>();
                Elements modelDivs = HtmlParser.getTags(body, "div[class~=model-wrapper]");
                LOG.debug("Found {} models", modelDivs.size());
                for (Element div : modelDivs) {
                    try {
                        models.add(SecretFriendsModelParser.parse(this, div));
                    } catch (Exception e) {
                        LOG.warn("Couldn't parse one of the models: {}", div.html(), e);
                    }
                }
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof SecretFriendsModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return false;
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https?://(?:www\\.)?secretfriends.com/friends/([^/]*?)/?").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }
}
