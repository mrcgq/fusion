package ctbrec.sites.flirt4free;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import ctbrec.Model;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.Request;
import okhttp3.Response;

public class Flirt4Free extends AbstractSite {

    public static final String BASE_URI = "https://www.flirt4free.com";
    private HttpClient httpClient;

    @Override
    public String getName() {
        return "Flirt4Free";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URI;
    }

    @Override
    public String getAffiliateLink() {
        return BASE_URI + "/live/girls/?mp_code=bsv7d";
    }

    @Override
    public String getBuyTokensLink() {
        return getAffiliateLink();
    }

    @Override
    public Model createModel(String name) {
        Flirt4FreeModel model = new Flirt4FreeModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + "/rooms/" + name + '/');
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        if (!credentialsAvailable()) {
            throw new IOException("Account settings not available");
        }

        login();

        String url = Flirt4Free.BASE_URI + "/my-account/";
        Request request = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try(Response response = getHttpClient().execute(request)) {
            if(response.isSuccessful()) {
                String body = response.body().string();
                String text = HtmlParser.getText(body, "span[class~=user-credit-amount] strong");
                return Double.parseDouble(text);
            } else {
                throw new RuntimeException("Tokens not found in response");
            }
        }
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new Flirt4FreeHttpClient(getConfig());
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
        String url = BASE_URI + "/search/?query=" + URLEncoder.encode(q, "utf-8");
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                List<Model> result = new ArrayList<>();
                String body = response.body().string();
                Elements modelLinks = HtmlParser.getTags(body, "form#advanced_search h4 a.common-link");
                for (Element link : modelLinks) {
                    String cellHtml = link.parent().parent().parent().html();
                    link.setBaseUri(BASE_URI);
                    String bioPage = link.absUrl("href");
                    Model model = createModelFromUrl(bioPage);
                    Element img = HtmlParser.getTag(cellHtml, "img[alt]");
                    model.setPreview(img.attr("src"));
                    model.setDisplayName(img.attr("alt"));
                    result.add(model);
                }
                return result;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof Flirt4FreeModel;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().flirt4freeUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        String[] patterns = {
                "https?://(?:www\\.)?flirt4free.com/\\?model=(.*?)",
                "https?://(?:www\\.)?flirt4free.com/rooms/(.*?)/?",
                "https?://(?:www\\.)?flirt4free.com/models/bios/(.*?)/about.php"
        };
        for (String p : patterns) {
            Matcher m = Pattern.compile(p).matcher(url);
            if (m.matches()) {
                String modelName = m.group(1);
                return createModel(modelName);
            }
        }
        return super.createModelFromUrl(url);
    }
}
