package ctbrec.ui.sites.winktv;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.winktv.WinkTv;
import ctbrec.sites.winktv.WinkTvModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

import static ctbrec.io.HttpConstants.*;

@Slf4j
public class WinkTvUpdateService extends PaginatedScheduledService {

    private static final String API_URL = "https://api.winktv.co.kr/v1/live";

    private final WinkTv site;
    protected int modelsPerPage = 48;
    protected Predicate<WinkTvModel> filter;

    public WinkTvUpdateService(WinkTv site, Predicate<WinkTvModel> filter) {
        this.site = site;
        this.filter = filter;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                return loadModelList()
                        .stream()
                        .filter(filter)
                        .map(Model.class::cast)
                        .toList();
            }
        };
    }

    private List<WinkTvModel> loadModelList() throws IOException {
        int offset = (page - 1) * modelsPerPage;
        log.debug("Fetching page {} offset:{}, limit:{}", API_URL, offset, modelsPerPage);


        FormBody body = new FormBody.Builder()
                .add("offset", String.valueOf(offset))
                .add("limit", String.valueOf(modelsPerPage))
                .add("orderBy", "hot")
                .add("onlyNewBj", "N")
                .build();
        Request req = new Request.Builder()
                .url(API_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .post(body)
                .build();
        try (var response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                List<WinkTvModel> result = new ArrayList<>();
                var content = response.body().string();
                var json = new JSONObject(content);
                if (json.optBoolean("result")) {
                    var modelNodes = json.getJSONArray("list");
                    parseModels(modelNodes, result);
                }
                return result;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void parseModels(JSONArray jsonModels, List<WinkTvModel> models) {
        for (var i = 0; i < jsonModels.length(); i++) {
            var m = jsonModels.getJSONObject(i);
            String name = m.optString("userId");
            WinkTvModel model = site.createModel(name);
            model.setDisplayName(m.getString("userNick"));
            boolean isAdult = m.optBoolean("isAdult");
            model.setAdult(isAdult);
            if (isAdult && m.has("ivsThumbnail")) {
                model.setPreview(m.optString("ivsThumbnail"));
            } else {
                model.setPreview(m.optString("thumbUrl"));
            }
            boolean isLive = m.optBoolean("isLive");
            if (isLive) models.add(model);
        }
    }

    public void setFilter(Predicate<WinkTvModel> filter) {
        this.filter = filter;
    }

}
