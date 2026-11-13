package ctbrec.ui.sites.streamray;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.sites.streamray.Streamray;
import ctbrec.sites.streamray.StreamrayHttpClient;
import ctbrec.sites.streamray.StreamrayModel;
import javafx.concurrent.Task;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static ctbrec.io.HttpConstants.*;

@Slf4j
public class StreamrayFavoritesService extends AbstractStreamrayUpdateService {

    private List<StreamrayModel> modelsList;
    private Instant lastListInfoRequest = Instant.EPOCH;
    @Getter
    private boolean loggedIn = false;
    private boolean showOnline = true;

    public StreamrayFavoritesService(Streamray site) {
        super(site);
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (showOnline) {
                    return getModelList().stream()
                            .skip((page - 1) * (long) MODELS_PER_PAGE)
                            .limit(MODELS_PER_PAGE)
                            .map(Model.class::cast)
                            .toList();
                } else {
                    return loadOfflineModelList().stream()
                            .map(Model.class::cast)
                            .toList();
                }
            }
        };
    }

    private List<StreamrayModel> getModelList() throws IOException {
        if (Duration.between(lastListInfoRequest, Instant.now()).getSeconds() < 30) {
            return Objects.nonNull(modelsList) ? modelsList : loadModelList(API_URL);
        }
        modelsList = loadModelList(API_URL);
        return modelsList;
    }

    private List<StreamrayModel> loadOfflineModelList() throws IOException {
        String url = "https://beta-api.cams.com/favorites/member_favorites/?gender=female";
        int offset = (getPage() - 1) * MODELS_PER_PAGE;
        String paginatedUrl = url + "&offset=" + offset + "&limit=" + MODELS_PER_PAGE;
        return loadModelList(paginatedUrl);
    }

    private List<StreamrayModel> loadModelList(String url) throws IOException {
        log.debug("Fetching page {}", url);
        lastListInfoRequest = Instant.now();
        StreamrayHttpClient client = (StreamrayHttpClient) site.getHttpClient();
        String token;
        if (site.login()) {
            loggedIn = true;
            token = client.getUserToken();
        } else {
            loggedIn = false;
            return Collections.emptyList();
        }
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, site.getBaseUrl() + "/")
                .header(ORIGIN, site.getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(AUTHORIZATION, "Bearer " + token)
                .build();
        try (Response response = client.execute(req)) {
            if (response.isSuccessful()) {
                List<StreamrayModel> models = new ArrayList<>();
                JSONObject json = new JSONObject(response.body().string());
                if (showOnline) {
                    if (json.has("models")) {
                        JSONArray modelNodes = json.getJSONArray("models");
                        AbstractStreamrayUpdateService.setMapping(json.getJSONArray("mapping"));
                        parseModels(modelNodes, models);
                    }
                } else {
                    if (json.has("results")) {
                        JSONArray modelNodes = json.getJSONArray("results");
                        parseOfflineModels(modelNodes, models);
                    }
                }
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void parseModels(JSONArray jsonModels, List<StreamrayModel> models) {
        int nameIdx = mappingIndex("stream_name");
        int favIdx = mappingIndex("is_favorite");
        for (int i = 0; i < jsonModels.length(); i++) {
            JSONArray m = jsonModels.getJSONArray(i);
            String name = m.optString(nameIdx);
            boolean favorite = m.optBoolean(favIdx);
            if (favorite) {
                StreamrayModel model = site.createModel(name);
                String preview = getPreviewURL(name);
                model.setPreview(preview);
                model.setTags(createTags(m));
                StringBuilder description = new StringBuilder();
                for (String tag : model.getTags()) {
                    description.append("#").append(tag).append(" ");
                }
                model.setDescription(description.toString());
                models.add(model);
            }
        }
    }

    private void parseOfflineModels(JSONArray jsonModels, List<StreamrayModel> models) {
        for (int i = 0; i < jsonModels.length(); i++) {
            JSONObject m = jsonModels.getJSONObject(i);
            String name = m.optString("stream_name");
            if (StringUtil.isBlank(name) || m.optBoolean("is_online")) {
                continue;
            }
            StreamrayModel model = site.createModel(name);
            String preview = getPreviewURL(name);
            model.setPreview(preview);
            model.setDisplayName(m.getString("screen_name"));
            model.setOnlineState(Model.State.OFFLINE);
            models.add(model);
        }
    }

    public void setOnline(boolean online) {
        showOnline = online;
    }
}
