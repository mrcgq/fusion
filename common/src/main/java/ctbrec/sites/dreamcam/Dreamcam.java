package ctbrec.sites.dreamcam;

import ctbrec.Model;
import ctbrec.io.HttpClient;
import ctbrec.sites.AbstractSite;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Dreamcam extends AbstractSite {

    public static String domain = "dreamcam.com";
    public static String baseUri = "https://dreamcam.com";
    private HttpClient httpClient;

    @Override
    public void init() throws IOException {
    }

    @Override
    public String getName() {
        return "DreamCam";
    }

    @Override
    public String getBaseUrl() {
        return baseUri;
    }

    @Override
    public String getAffiliateLink() {
        return baseUri;
    }

    @Override
    public String getBuyTokensLink() {
        return getAffiliateLink();
    }

    @Override
    public DreamcamModel createModel(String name) {
        DreamcamModel model = new DreamcamModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + "/models/" + name);
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        return 0d;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new DreamcamHttpClient(getConfig());
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
        return false;
    }

    @Override
    public boolean searchRequiresLogin() {
        return false;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        return Collections.emptyList();
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof DreamcamModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return false;
    }

    @Override
    public Model createModelFromUrl(String url) {
        String[] patterns = {
                "https://.*?dreamcam.com/models/([_a-zA-Z0-9]+)",
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
