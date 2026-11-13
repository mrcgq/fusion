package ctbrec.ui.sites.flirt4free;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.sites.flirt4free.Flirt4FreeModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.json.JSONObject;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import static ctbrec.io.HttpClient.gunzipBody;
import static ctbrec.io.HttpConstants.*;

public class Flirt4FreeUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(Flirt4FreeUpdateService.class);
    private static final int MODELS_PER_PAGE = 50;
    private final String url;
    private final Flirt4Free flirt4Free;
    private final Predicate<Flirt4FreeModel> filter;

    public Flirt4FreeUpdateService(Flirt4Free flirt4Free, String url, Predicate<Flirt4FreeModel> filter) {
        this.flirt4Free = flirt4Free;
        this.url = url;
        this.filter = filter;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                LOG.debug("Fetching page {}", url);
                var request = new Request.Builder()
                        .url(url)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(ACCEPT_ENCODING, ACCEPT_ENCODING_GZIP)
                        .build();
                try (var response = flirt4Free.getHttpClient().execute(request)) {
                    if (response.isSuccessful()) {
                        var body = gunzipBody(response);
                        return parseResponse(body);
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }
        };
    }

    private List<Model> parseResponse(String body) throws IOException {
        List<Flirt4FreeModel> models = new ArrayList<>();
        var m = Pattern.compile("window\\.__homePageData__ = (\\{.*})", Pattern.DOTALL).matcher(body);
        if (m.find()) {
            var data = new JSONObject(m.group(1));
            var modelData = data.getJSONArray("models");
            LOG.debug("Found {} models", modelData.length());
            for (var i = 0; i < modelData.length(); i++) {
                var modelJson = modelData.getJSONObject(i);
                try {
                    Flirt4FreeModel model = parseModel(modelJson);
                    models.add(model);
                } catch (Exception e) {
                    LOG.warn("Couldn't parse model {}", modelJson);
                }
            }
            return models.stream()
                    .filter(filter)
                    .skip((page - 1) * (long) MODELS_PER_PAGE)
                    .limit(MODELS_PER_PAGE)
                    .map(Model.class::cast)
                    .toList();
        } else {
            throw new IOException("Pattern didn't match model JSON data");
        }
    }

    private Flirt4FreeModel parseModel(JSONObject modelData) {
        var name = modelData.getString("model_seo_name");
        Flirt4FreeModel model = (Flirt4FreeModel) flirt4Free.createModel(name);
        model.setDisplayName(Entities.unescape(modelData.getString("display")));
        model.setDescription("");
        model.setUrl(Flirt4Free.BASE_URI + "/rooms/" + model.getName() + '/');
        model.setNew(modelData.optString("is_new", "0").equals("1"));
        var videoHost = modelData.getString("video_host");
        var modelId = modelData.getString("model_id");
        model.setId(modelId);
        String streamUrl = "https://manifest.vscdns.com/manifest.m3u8.m3u8?key=nil&provider=level3&secure=true&host=" + videoHost + "&model_id=" + modelId;
        model.setStreamUrl(streamUrl);
        model.setPreview("https://live-screencaps.vscdns.com/" + modelId + "-desktop.jpg");
        model.setOnlineState(ctbrec.Model.State.ONLINE);
        if (modelData.has("category_id")) {
            model.getCategories().add(modelData.getString("category_id"));
        }
        if (modelData.has("category_id_2")) {
            model.getCategories().add(modelData.getString("category_id_2"));
        }
        if (modelData.has("category_id_3")) {
            model.getCategories().add(modelData.getString("category_id_3"));
        }
        if (modelData.has("video_width") && modelData.has("video_aspect_ratio")) {
            String[] aspectRatioParts = modelData.getString("video_aspect_ratio").split(":");
            double aspectWidth = Integer.parseInt(aspectRatioParts[0]);
            double aspectHeight = Integer.parseInt(aspectRatioParts[1]);
            int width = Integer.parseInt(modelData.getString("video_width"));
            int height = (int) Math.ceil(width * aspectHeight / aspectWidth);
            model.setStreamResolution(new int[]{width, height});
        }
        return model;
    }
}
