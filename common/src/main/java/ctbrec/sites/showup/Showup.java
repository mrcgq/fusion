package ctbrec.sites.showup;

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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Showup extends AbstractSite {

    private static final Logger LOG = LoggerFactory.getLogger(Showup.class);
    public static final String BASE_URL = "https://showup.tv";

    private ShowupHttpClient httpClient;
    private Instant lastModelListUpdate = Instant.EPOCH;
    private List<Model> models = new ArrayList<>();

    @Override
    public String getName() {
        return "Showup.tv";
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
    public ShowupModel createModel(String name) {
        ShowupModel model = new ShowupModel();
        model.setSite(this);
        model.setName(name);
        model.setUrl(BASE_URL + '/' + safeUrlEncode(name));
        return model;
    }

    private String safeUrlEncode(String s) {
        return URLEncoder.encode(s, UTF_8);
    }

    public List<Model> getModelList() throws IOException {
        return getModelList(false);
    }

    public List<Model> getModelList(boolean forceUpdate) throws IOException {
        if (Duration.between(lastModelListUpdate, Instant.now()).getSeconds() > 10 || forceUpdate) {
            lastModelListUpdate = Instant.now();
            String url = getBaseUrl() + "/site/get_stream_list/big";
            Request req = new Request.Builder()
                    .url(url)
                    .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                    .build();

            try (Response response = getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                    LOG.trace(body);
                    JSONObject json = new JSONObject(body);
                    models = new ArrayList<>();
                    JSONArray list = json.getJSONArray("list");
                    for (int i = 0; i < list.length(); i++) {
                        JSONObject entry = list.getJSONObject(i);
                        ShowupModel model = createModel(entry.getString("username"));
                        model.setUid(entry.optString("uid", null));
                        model.setPreview(getBaseUrl() + "/files/" + entry.optString("small_img") + ".jpg");
                        model.setDescription(entry.optString("description"));
                        model.setName(entry.optString("username"));
                        model.setUrl(getBaseUrl() + '/' + model.getName());
                        // TODO figure what these flags mean, because you can still stream the models, if is_prv or is_group is set ?!?
                        //                    if(entry.optInt("is_group") == 1) {
                        //                        model.setOnlineState(GROUP);
                        //                    } else if(entry.optInt("is_prv") == 1) {
                        //                        model.setOnlineState(PRIVATE);
                        //                    } else {
                        //                        model.setOnlineState(ONLINE);
                        //                    }
                        model.setOnlineState(ONLINE);
                        model.setStreamId(entry.optString("stream_id"));
                        model.setStreamTranscoderAddr(entry.optString("stream_transcoder_addr"));
                        model.setSite(this);
                        models.add(model);
                    }
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
        return models;
    }

    @Override
    public Double getTokenBalance() throws IOException {
        return 0d;
    }

    @Override
    public String getBuyTokensLink() {
        return getBaseUrl();
    }

    @Override
    public boolean login() throws IOException {
        return getHttpClient().login();
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new ShowupHttpClient(getConfig());
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
        return false;
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof ShowupModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return false;
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher matcher = Pattern.compile("https?://(?:.*?\\.)?showup.tv(?:/profile)?/(.*)").matcher(url);
        if (matcher.find()) {
            return createModel(matcher.group(1));
        } else {
            return super.createModelFromUrl(url);
        }
    }

}
