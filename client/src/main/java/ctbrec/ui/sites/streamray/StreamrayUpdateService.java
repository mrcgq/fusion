package ctbrec.ui.sites.streamray;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.streamray.Streamray;
import ctbrec.sites.streamray.StreamrayHttpClient;
import ctbrec.sites.streamray.StreamrayModel;
import javafx.concurrent.Task;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import static ctbrec.io.HttpConstants.*;

@Slf4j
public class StreamrayUpdateService extends AbstractStreamrayUpdateService {

    private List<StreamrayModel> modelsList;
    private Instant lastListInfoRequest = Instant.EPOCH;

    @Setter
    protected Predicate<StreamrayModel> filter;

    public StreamrayUpdateService(Streamray site, Predicate<StreamrayModel> filter) {
        super(site);
        this.filter = filter;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                return getModelList().stream()
                        .filter(filter)
                        .skip((page - 1) * (long) MODELS_PER_PAGE)
                        .limit(MODELS_PER_PAGE)
                        .map(Model.class::cast)
                        .toList();
            }
        };
    }

    private List<StreamrayModel> getModelList() throws IOException {
        if (Duration.between(lastListInfoRequest, Instant.now()).getSeconds() < 30) {
            return Objects.nonNull(modelsList) ? modelsList : loadModelList();
        }
        modelsList = loadModelList();
        return modelsList;
    }

    private List<StreamrayModel> loadModelList() throws IOException {
        log.debug("Fetching page {}", API_URL);
        lastListInfoRequest = Instant.now();
        StreamrayHttpClient client = (StreamrayHttpClient) site.getHttpClient();
        Request req = new Request.Builder()
                .url(API_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, site.getBaseUrl() + "/")
                .header(ORIGIN, site.getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = client.execute(req)) {
            if (response.isSuccessful()) {
                List<StreamrayModel> models = new ArrayList<>();
                String content = response.body().string();
                JSONObject json = new JSONObject(content);
                JSONArray modelNodes = json.getJSONArray("models");
                AbstractStreamrayUpdateService.setMapping(json.getJSONArray("mapping"));
                parseModels(modelNodes, models);
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void parseModels(JSONArray jsonModels, List<StreamrayModel> models) {
        int nameIdx = mappingIndex("stream_name");
        int dateIdx = mappingIndex("create_date");
        int genIdx = mappingIndex("gender");
        for (var i = 0; i < jsonModels.length(); i++) {
            var m = jsonModels.getJSONArray(i);
            String name = m.optString(nameIdx);
            String gender = m.optString(genIdx);
            String reg = m.optString(dateIdx);
            StreamrayModel model = site.createModel(name);
            try {
                LocalDate regDate = LocalDate.parse(reg, DateTimeFormatter.BASIC_ISO_DATE);
                model.setRegDate(regDate);
            } catch (DateTimeParseException e) {
                model.setRegDate(LocalDate.EPOCH);
            }
            String preview = getPreviewURL(name);
            model.setPreview(preview);
            model.setGender(gender);
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
