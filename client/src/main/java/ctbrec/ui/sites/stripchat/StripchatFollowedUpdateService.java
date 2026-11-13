package ctbrec.ui.sites.stripchat;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.sites.stripchat.StripchatHttpClient;
import ctbrec.sites.stripchat.StripchatModel;
import ctbrec.ui.SiteUiFactory;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;

import static ctbrec.io.HttpConstants.*;

@Slf4j
public class StripchatFollowedUpdateService extends AbstractStripchatUpdateService {

    private static final int MODELS_PER_PAGE = 60;
    private final Stripchat stripchat;
    private final boolean loginRequired;
    private String url;

    public StripchatFollowedUpdateService(Stripchat stripchat) {
        this.stripchat = stripchat;
        this.loginRequired = true;
        this.url = stripchat.getBaseUrl() + "/api/front/models/favorites?sortBy=lastAdded";
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (loginRequired && !stripchat.credentialsAvailable()) {
                    return Collections.emptyList();
                } else {
                    int offset = (getPage() - 1) * MODELS_PER_PAGE;
                    int limit = MODELS_PER_PAGE;
                    String paginatedUrl = url + "&offset=" + offset + "&limit=" + limit + "&uniq=" + getUniq();
                    log.debug("Fetching page {}", paginatedUrl);
                    if (loginRequired) {
                        SiteUiFactory.getUi(stripchat).login();
                    }
                    String jwtToken = ((StripchatHttpClient) stripchat.getHttpClient()).getJwtToken();
                    Request request = new Request.Builder()
                            .url(paginatedUrl)
                            .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header("Authorization", Optional.ofNullable(jwtToken).orElse(""))
                            .build();
                    try (Response response = stripchat.getHttpClient().execute(request)) {
                        if (response.isSuccessful()) {
                            List<Model> models = new ArrayList<>();
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.has("totalCount") && offset >= json.getInt("totalCount")) {
                                return Collections.emptyList();
                            }
                            if (json.has("models")) {
                                JSONArray jsonModels = json.getJSONArray("models");
                                models.addAll(parseModels(jsonModels));
                            } else {
                                log.debug("Response was not successful: {}", json);
                                return Collections.emptyList();
                            }
                            return models;
                        } else {
                            throw new HttpException(response.code(), response.message());
                        }
                    }
                }
            }
        };
    }

    private List<Model> parseModels(JSONArray jsonModels) {
        List<Model> models = new ArrayList<>();
        for (var i = 0; i < jsonModels.length(); i++) {
            var jsonModel = jsonModels.getJSONObject(i);
            try {
                StripchatModel model = stripchat.createModel(jsonModel.getString("username"));
                model.setPreview(getPreviewUrl(jsonModel));
                model.setDisplayName(model.getName());
                String status = jsonModel.optString("status");
                mapOnlineState(model, status);
                model.setTags(createTags(jsonModel));
                StringBuilder description = new StringBuilder();
                for (String tag : model.getTags()) {
                    description.append("#").append(tag).append(" ");
                }
                model.setDescription(description.toString());
                models.add(model);
            } catch (Exception e) {
                log.warn("Couldn't parse one of the models: {}", jsonModel, e);
            }
        }
        return models;
    }

    private void mapOnlineState(StripchatModel model, String status) {
        switch (status) {
            case "public" -> model.setOnlineState(Model.State.ONLINE);
            case "idle" -> model.setOnlineState(Model.State.AWAY);
            case "private", "p2p", "groupShow", "virtualPrivate" -> model.setOnlineState(Model.State.PRIVATE);
            default -> model.setOnlineState(Model.State.OFFLINE);
        }
    }

    public void setOnline(boolean online) {
        if (online) {
            url = stripchat.getBaseUrl() + "/api/front/models/favorites?sortBy=lastAdded";
        } else {
            url = stripchat.getBaseUrl() + "/api/front/models/favorites/offline?sortBy=lastAdded";
        }
    }
}
