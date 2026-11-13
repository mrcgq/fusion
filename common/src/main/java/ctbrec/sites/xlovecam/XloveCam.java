package ctbrec.sites.xlovecam;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.sites.AbstractSite;

public class XloveCam extends AbstractSite {

    public static String baseUrl = "https://www.xlovecam.com";
    public static String mobileUrl = "https://mobile.xlovecam.com";
    private HttpClient httpClient;

    @Override
    public void init() throws IOException {
        // noop
    }

    @Override
    public String getName() {
        return "XloveCam";
    }

    @Override
    public String getBaseUrl() {
        return mobileUrl;
    }

    @Override
    public String getAffiliateLink() {
        return getBaseUrl() + "/en/register/";
    }

    @Override
    public String getBuyTokensLink() {
        return getBaseUrl();
    }

    @Override
    public XloveCamModel createModel(String name) {
        XloveCamModel model = new XloveCamModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + "/en/model/" + name);
        model.setSite(this);
        return model;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        return Double.valueOf(0);
    }

    @Override
    public synchronized boolean login() throws IOException {
        return credentialsAvailable() && getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new XloveCamHttpClient(getConfig());
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
        Map<String, String> filter = new HashMap<>();
        filter.put("config[nickname]", q);
        filter.put("config[favorite]", "0");
        filter.put("config[recent]", "0");
        filter.put("config[vip]", "0");
        filter.put("origin", "filter-chg");
        filter.put("stat", "0");
        return new XloveCamModelLoader(this).loadModelList(1, filter);
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof XloveCamModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return StringUtil.isNotBlank(getConfig().getSettings().xlovecamUsername);
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https://(?:[a-z]+.)xlovecam.com/[a-z]{2}/model/(.*?)(?:/.*)*$").matcher(url);
        if (m.matches()) {
            String modelName = m.group(1);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }


}
