package ctbrec.sites.streamray;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Model.State;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.sites.AbstractSite;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;


@Slf4j
public class Streamray extends AbstractSite {

    public static final String BASE_URI = "https://streamray.com";
    public static final String API_URL = "https://beta-api.cams.com";
    @Getter
    private boolean loggedIn = false;

    private StreamrayHttpClient httpClient;

    @Override
    public String getName() {
        return "Streamray";
    }

    @Override
    public String getBaseUrl() {
        return BASE_URI;
    }

    public String getApiUrl() {
        return API_URL;
    }

    @Override
    public StreamrayModel createModel(String name) {
        StreamrayModel model = new StreamrayModel();
        model.setName(name);
        model.setUrl(getBaseUrl() + '/' + name);
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
        return getBaseUrl();
    }

    @Override
    public synchronized boolean login() throws IOException {
        if (!loggedIn) {
            boolean result = getHttpClient().login();
            log.debug("Streamray site login call result: {}", result);
            loggedIn = result;
            return result;
        } else {
            return loggedIn;
        }
    }

    @Override
    public HttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new StreamrayHttpClient(getConfig());
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
        return true;
    }

    @Override
    public boolean supportsSearch() {
        return true;
    }

    @Override
    public List<Model> search(String q) throws IOException, InterruptedException {
        if (StringUtil.isBlank(q)) {
            return Collections.emptyList();
        }
        String url = getApiUrl() + "/models/new/?limit=30&search=" + URLEncoder.encode(q, UTF_8) + "&order=is_online";
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getConfig().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.has("results")) {
                    List<Model> models = new ArrayList<>();
                    JSONArray results = json.getJSONArray("results");
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject result = results.getJSONObject(i);
                        StreamrayModel model = createModel(result.getString("stream_name"));
                        String image = result.optString("profile_image");
                        if (StringUtil.isBlank(image)) {
                            image = getPreviewURL(model.getName());
                        }
                        model.setPreview(image);
                        models.add(model);
                    }
                    return models;
                } else {
                    return Collections.emptyList();
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean isSiteForModel(Model m) {
        return m instanceof StreamrayModel;
    }

    @Override
    public boolean credentialsAvailable() {
        return true;
    }

    @Override
    public Model createModelFromUrl(String url) {
        Matcher m = Pattern.compile("https://(streamray|cams).com/(\\w+)").matcher(url);
        if (m.matches()) {
            String modelName = m.group(2);
            return createModel(modelName);
        } else {
            return super.createModelFromUrl(url);
        }
    }

    @Override
    public String getAffiliateLink() {
        return getBaseUrl();
    }


    public List<StreamrayModel> loadModelList() throws IOException {
        return loadModelList(false);
    }

    public List<StreamrayModel> loadModelList(boolean withLogin) throws IOException {
        String url = API_URL + "/won/compressed/";
        log.debug("Fetching page {}", url);
        StreamrayHttpClient client = (StreamrayHttpClient) getHttpClient();
        String token;
        Request.Builder builder = new Request.Builder().url(url);
        if (withLogin) {
            if (login()) {
                token = client.getUserToken();
                builder.header(AUTHORIZATION, "Bearer " + token);
            } else {
                return Collections.emptyList();
            }
        }
        builder.header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getBaseUrl() + "/")
                .header(ORIGIN, getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST);
        Request req = builder.build();
        try (Response response = client.execute(req)) {
            if (response.isSuccessful()) {
                List<StreamrayModel> models = new ArrayList<>();
                String content = response.body().string();
                JSONObject json = new JSONObject(content);
                parseModels(json, models);
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void parseModels(JSONObject json, List<StreamrayModel> models) {
        JSONArray mapping = json.getJSONArray("mapping");
        JSONArray jsonModels = json.getJSONArray("models");
        int nameIdx = indexOfProperty(mapping, "stream_name");
        int dateIdx = indexOfProperty(mapping, "create_date");
        int genIdx = indexOfProperty(mapping, "gender");
        int chatTypeIdx = indexOfProperty(mapping, "chat_type");
        int favIdx = indexOfProperty(mapping, "is_favorite");
        for (var i = 0; i < jsonModels.length(); i++) {
            var m = jsonModels.getJSONArray(i);
            String name = m.optString(nameIdx);
            StreamrayModel model = createModel(name);
            try {
                LocalDate regDate = LocalDate.parse(m.optString(dateIdx), DateTimeFormatter.BASIC_ISO_DATE);
                model.setRegDate(regDate);
            } catch (DateTimeParseException e) {
                model.setRegDate(LocalDate.EPOCH);
            }
            model.setOnlineState(mapOnlineState(m.optString(chatTypeIdx)));
            String preview = getPreviewURL(name);
            model.setPreview(preview);
            model.setGender(m.optString(genIdx));
            model.setFavorite(m.optBoolean(favIdx));
            models.add(model);
        }
    }

    public State mapOnlineState(String status) {
        boolean goalShows = Config.getInstance().getSettings().streamrayRecordGoalShows;
        return switch (status) {
            case "0" -> OFFLINE;
            case "1" -> ONLINE;
            case "6" -> goalShows ? ONLINE : PRIVATE;
            case "2", "3", "4", "7", "10", "11", "12", "13", "14" -> PRIVATE;
            default -> UNKNOWN;
        };
    }

    private int indexOfProperty(JSONArray mapping, String key) {
        for (var i = 0; i < mapping.length(); i++) {
            if (Objects.equals(key, mapping.get(i))) return i;
        }
        return -1;
    }

    private String getPreviewURL(String name) {
        String lname = name.toLowerCase();
        String url = MessageFormat.format("https://images4.streamray.com/images/streamray/won/jpg/{0}/{1}/{2}_640.jpg", lname.substring(0, 1), lname.substring(lname.length() - 1), lname);
        try {
            return MessageFormat.format("https://dynimages.securedataimages.com/unsigned/rs:fill:640::0/g:no/plain/{0}@jpg", URLEncoder.encode(url, UTF_8));
        } catch (Exception ex) {
            return url;
        }
    }
}
