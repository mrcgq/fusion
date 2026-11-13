package ctbrec.ui.sites.stripchat;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.sites.stripchat.StripchatModel;
import ctbrec.ui.SiteUiFactory;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static ctbrec.io.HttpConstants.*;

public class StripchatUpdateService extends AbstractStripchatUpdateService {

    private static final Logger LOG = LoggerFactory.getLogger(StripchatUpdateService.class);

    private final String url;
    private final boolean loginRequired;
    private final Stripchat stripchat;
    int modelsPerPage = 48;

    public StripchatUpdateService(String url, boolean loginRequired, Stripchat stripchat) {
        this.url = url;
        this.loginRequired = loginRequired;
        this.stripchat = stripchat;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (loginRequired && !stripchat.credentialsAvailable()) {
                    return Collections.emptyList();
                } else {
                    int offset = (getPage() - 1) * modelsPerPage;
                    int limit = modelsPerPage;
                    String paginatedUrl = url + "&offset=" + offset + "&limit=" + limit;
                    LOG.debug("Fetching page {}", paginatedUrl);
                    if (loginRequired) {
                        SiteUiFactory.getUi(stripchat).login();
                    }
                    var request = new Request.Builder()
                            .url(paginatedUrl)
                            .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .build();
                    try (var response = stripchat.getHttpClient().execute(request)) {
                        if (response.isSuccessful()) {
                            return parseModels(response.body().string());
                        } else {
                            throw new HttpException(response.code(), response.message());
                        }
                    }
                }

            }
        };
    }

    private List<Model> parseModels(String body) {
        List<Model> models = new ArrayList<>();
        var json = new JSONObject(body);
        if (json.has("models")) {
            var jsonModels = json.getJSONArray("models");
            for (var i = 0; i < jsonModels.length(); i++) {
                var jsonModel = jsonModels.getJSONObject(i);
                try {
                    StripchatModel model = stripchat.createModel(jsonModel.getString("username"));
                    model.setPreview(getPreviewUrl(jsonModel));
                    model.setDisplayName(model.getName());
                    model.setOnlineState(Model.State.ONLINE);
                    model.setTags(createTags(jsonModel));
                    StringBuilder description = new StringBuilder();
                    for (String tag : model.getTags()) {
                        description.append("#").append(tag).append(" ");
                    }
                    model.setDescription(description.toString());
                    models.add(model);
                } catch (Exception e) {
                    LOG.warn("Couldn't parse one of the models: {}", jsonModel, e);
                }
            }
            return models;
        } else {
            LOG.debug("Response was not successful: {}", json);
            return Collections.emptyList();
        }
    }
}
