package ctbrec.sites.fc2live;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ctbrec.Model;
import ctbrec.io.HttpClient;
import ctbrec.sites.AbstractSite;

public class Fc2Live extends AbstractSite {

    public static final String BASE_URL = "https://live.fc2.com";
    private Fc2HttpClient httpClient;

    @Override
    public String getName() {
        return "FC2Live";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URL;
    }

    @Override
    public String getAffiliateLink() {
        return BASE_URL + "/?afid=98987181";
    }

    @Override
    public Model createModel(String name) {
        name = name.replace("/", "_");
        Fc2Model model = new Fc2Model();
        model.setSite(this);
        model.setName(name);
        return model;
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("http.*?fc2.*?.com/(\\d+)/?").matcher(url);
        if (m.find()) {
            Fc2Model model = (Fc2Model) createModel("");
            model.setId(m.group(1));
            try {
                model.loadModelInfo();
                model.setUrl(url);
            } catch (IOException e) {
                return null;
            }
            return model;
        } else {
            return super.createModelFromUrl(url);
        }
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
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new Fc2HttpClient(getConfig());
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
    public boolean isSiteForModel(Model m) {
        return m instanceof Fc2Model;
    }

    @Override
    public boolean credentialsAvailable() {
        return !getConfig().getSettings().fc2liveUsername.isEmpty();
    }

}
