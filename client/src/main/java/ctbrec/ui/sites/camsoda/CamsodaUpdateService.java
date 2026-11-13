package ctbrec.ui.sites.camsoda;

import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.camsoda.CamsodaModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;

@Slf4j
public class CamsodaUpdateService extends PaginatedScheduledService {

    protected final String url;
    protected final boolean loginRequired;
    protected final Camsoda camsoda;
    protected int modelsPerPage = 60;
    private static List<CamsodaModel> modelsList;
    private static Instant lastListInfoRequest = Instant.EPOCH;
    @Setter
    protected Predicate<CamsodaModel> filter;

    public CamsodaUpdateService(String url, boolean loginRequired, Camsoda camsoda, Predicate<CamsodaModel> filter) {
        this.url = url;
        this.loginRequired = loginRequired;
        this.camsoda = camsoda;
        this.filter = filter;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                return getModelList().stream()
                        .sorted((m1, m2) -> (int) (m2.getSortOrder() - m1.getSortOrder()))
                        .filter(filter)
                        .skip((page - 1) * (long) modelsPerPage)
                        .limit(modelsPerPage)
                        .collect(Collectors.toList()); // NOSONAR
            }
        };
    }

    private List<CamsodaModel> getModelList() throws IOException {
        if (Objects.nonNull(modelsList) && Duration.between(lastListInfoRequest, Instant.now()).getSeconds() < 30) {
            return modelsList;
        }
        lastListInfoRequest = Instant.now();
        modelsList = loadOnlineModels();
        return Optional.ofNullable(modelsList).orElse(Collections.emptyList());
    }

    protected List<CamsodaModel> loadOnlineModels() throws IOException {
        if (loginRequired && StringUtil.isBlank(ctbrec.Config.getInstance().getSettings().camsodaUsername)) {
            return Collections.emptyList();
        } else {
            log.debug("Fetching page {}", url);
            if (loginRequired) {
                SiteUiFactory.getUi(camsoda).login();
            }
            var request = new Request.Builder().url(url).build();
            try (var response = camsoda.getHttpClient().execute(request)) {
                if (response.isSuccessful()) {
                    return parseModels(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
    }

    protected List<CamsodaModel> parseModels(String body) {
        List<CamsodaModel> models = new ArrayList<>();
        var json = new JSONObject(body);
        var template = json.getJSONArray("template");
        var results = json.getJSONArray("results");
        for (var i = 0; i < results.length(); i++) {
            var result = results.getJSONObject(i);
            var templateObject = result.get("tpl");
            try {
                if (templateObject instanceof JSONObject) {
                    parseModelFromObject(result, templateObject, template, models);
                } else if (templateObject instanceof JSONArray) {
                    parseModelFromArray(result, templateObject, template, models);
                }
            } catch (Exception e) {
                log.warn("Couldn't parse one of the models: {}", result, e);
            }
        }
        return models;
    }

    private void parseModelFromArray(JSONObject result, Object templateObject, JSONArray template, List<CamsodaModel> models) {
        var tpl = (JSONArray) templateObject;
        var name = tpl.getString(getTemplateIndex(template, "username"));
        CamsodaModel model = (CamsodaModel) camsoda.createModel(name);
        model.setSortOrder(tpl.getFloat(getTemplateIndex(template, "sort_value")));
        model.setDescription(tpl.getString(getTemplateIndex(template, "subject_html")));
        model.setNew(result.optBoolean("new"));
        model.setVoyeur(result.optBoolean("voyeur"));
        var preview = tpl.getString(getTemplateIndex(template, "thumb"));
        if (preview.startsWith("//")) {
            preview = "https:" + preview;
        }
        model.setPreview(preview);
        log.trace("Preview: {}", preview);
        model.setOnlineStateByStatus(tpl.getString(getTemplateIndex(template, "status")));
        var displayName = tpl.getString(getTemplateIndex(template, "display_name"));
        model.setDisplayName(displayName.replaceAll("[^a-zA-Z0-9]", ""));
        if (model.getDisplayName().isBlank()) {
            model.setDisplayName(name);
        }
        models.add(model);
    }

    private void parseModelFromObject(JSONObject result, Object templateObject, JSONArray template, List<CamsodaModel> models) {
        var tpl = (JSONObject) templateObject;
        var name = tpl.getString(Integer.toString(getTemplateIndex(template, "username")));
        CamsodaModel model = (CamsodaModel) camsoda.createModel(name);
        model.setDescription(tpl.getString(Integer.toString(getTemplateIndex(template, "subject_html"))));
        model.setSortOrder(tpl.getFloat(Integer.toString(getTemplateIndex(template, "sort_value"))));
        model.setNew(result.optBoolean("new"));
        model.setVoyeur(result.optBoolean("voyeur"));
        model.setGender(tpl.getString(Integer.toString(getTemplateIndex(template, "gender"))));
        var preview = tpl.getString(Integer.toString(getTemplateIndex(template, "thumb")));
        if (preview.startsWith("//")) {
            preview = "https:" + preview;
        }
        model.setPreview(preview);
        var displayName = tpl.getString(Integer.toString(getTemplateIndex(template, "display_name")));
        model.setDisplayName(displayName.replaceAll("[^a-zA-Z0-9]", ""));
        if (model.getDisplayName().isBlank()) {
            model.setDisplayName(name);
        }
        model.setOnlineState(tpl.getString(Integer.toString(getTemplateIndex(template, "stream_name"))).isEmpty() ? OFFLINE : ONLINE);
        parseStatus(template, tpl, model);
        models.add(model);
    }

    private void parseStatus(JSONArray template, JSONObject tpl, CamsodaModel model) {
        try {
            var statusIndex = Integer.toString(getTemplateIndex(template, "status"));
            if (tpl.has(statusIndex)) {
                model.setOnlineStateByStatus(tpl.getString(statusIndex));
            }
        } catch (NoSuchElementException e) {
            // that's ok
        }
    }

    private int getTemplateIndex(JSONArray template, String string) {
        for (var i = 0; i < template.length(); i++) {
            var s = template.getString(i);
            if (Objects.equals(s, string)) {
                return i;
            }
        }
        throw new NoSuchElementException(string + " not found in template: " + template);
    }
}
