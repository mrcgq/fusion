package ctbrec.sites.mfc;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.select.Elements;

import ctbrec.Model;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import okhttp3.Request;
import okhttp3.Response;

public class MyFreeCams extends AbstractSite {

    static String baseUrl = "https://www.myfreecams.com";

    private MyFreeCamsClient client;
    private MyFreeCamsHttpClient httpClient;

    @Override
    public void init() throws IOException {
        baseUrl = getConfig().getSettings().mfcBaseUrl;
        client = MyFreeCamsClient.getInstance();
        client.setSite(this);
        client.start();
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public String getName() {
        return "MyFreeCams";
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public String getAffiliateLink() {
        return baseUrl + "/?baf=8127165";
    }

    @Override
    public MyFreeCamsModel createModel(String name) {
        MyFreeCamsModel model = new MyFreeCamsModel(this);
        model.setName(name);
        model.setUrl("https://profiles.myfreecams.com/" + name);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        Request req = new Request.Builder()
                .url(baseUrl + "/php/account.php?request=status")
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .header(CONNECTION, KEEP_ALIVE)
                .build();
        try(Response response = getHttpClient().execute(req)) {
            if(response.isSuccessful()) {
                String content = Objects.requireNonNull(response.body(), "HTTP response is null").string();
                Elements tags = HtmlParser.getTags(content, "div.content > p > b");
                String tokens = tags.get(2).text();
                return Double.parseDouble(tokens);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public String getBuyTokensLink() {
        return baseUrl + "/php/purchase.php?request=tokens";
    }

    @Override
    public MyFreeCamsHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new MyFreeCamsHttpClient(getConfig());
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
        return true;
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        return client.search(q);
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof MyFreeCamsModel;
    }

    public MyFreeCamsClient getClient() {
        return client;
    }

    @Override
    public boolean credentialsAvailable() {
        String username = getConfig().getSettings().mfcUsername;
        return username != null && !username.trim().isEmpty();
    }

    @Override
    public Model createModelFromUrl(String url) {
        String[] patterns = new String[] {
                "https?://profiles.myfreecams.com/([^/]*?)",
                "https?://(?:www.)?myfreecams.com/#(.*)",
                "https?://m.myfreecams.com/models/(.*)"
        };
        for (String pattern : patterns) {
            Matcher m = Pattern.compile(pattern).matcher(url);
            if(m.matches()) {
                String modelName = m.group(1);
                return createModel(modelName);
            }
        }
        return super.createModelFromUrl(url);
    }

    @Override
    public String getTitle() {
        return getName();
    }
}
