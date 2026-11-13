package ctbrec.ui.sites.manyvids;

import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.manyvids.MVLive;
import ctbrec.sites.manyvids.MVLiveModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static ctbrec.io.HttpConstants.*;

public class MVLiveUpdateService extends PaginatedScheduledService {

    private final MVLive mvlive;
    private final String url;
    private final int modelsPerPage = 48;
    private static List<Model> modelsList;
    private static Instant lastListInfoRequest = Instant.EPOCH;

    private static final Logger LOG = LoggerFactory.getLogger(MVLiveUpdateService.class);

    public MVLiveUpdateService(MVLive site, String url) {
        this.mvlive = site;
        this.url = url;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                return getModelList().stream()
                        .skip((page - 1) * (long) modelsPerPage)
                        .limit(modelsPerPage)
                        .collect(Collectors.toList()); // NOSONAR
            }
        };
    }

    private List<Model> getModelList() throws IOException {
        if (Duration.between(lastListInfoRequest, Instant.now()).getSeconds() < 30) {
            return modelsList;
        }
        lastListInfoRequest = Instant.now();
        modelsList = loadModels(url);
        if (modelsList == null) {
            return Collections.emptyList();
        }
        return modelsList;
    }

    protected List<Model> loadModels(String url) throws IOException {
        List<Model> models = new ArrayList<>();
        LOG.debug("Loading live models from {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(ORIGIN, MVLive.BASE_URL)
                .header(REFERER, MVLive.BASE_URL)
                .header(AUTHORIZATION, "GUEST")
                .build();
        try (Response response = mvlive.getHttpClient().execute(req)) {
            String body = response.body().string();
            LOG.trace("response body: {}", body);
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                if (!json.has("live_creators")) {
                    LOG.debug("Unexpected response:\n{}", json.toString(2));
                    return Collections.emptyList();
                }
                JSONArray creators = json.getJSONArray("live_creators");
                for (int i = 0; i < creators.length(); i++) {
                    JSONObject creator = creators.getJSONObject(i);
                    MVLiveModel model = mvlive.createModel(creator.getString("url_handle"));
                    model.updateStateFromJson(creator);
                    models.add(model);
                }
                if (!json.optString("next_token").isBlank()) {
                    models.addAll(loadModels(url + "?next_token=" + json.optString("next_token")));
                }
            } else {
                throw new HttpException(response.code(), body);
            }
        }
        return models;
    }
}
