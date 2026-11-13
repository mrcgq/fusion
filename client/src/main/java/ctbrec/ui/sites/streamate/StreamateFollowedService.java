package ctbrec.ui.sites.streamate;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.streamate.Streamate;
import ctbrec.sites.streamate.StreamateHttpClient;
import ctbrec.sites.streamate.StreamateModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.*;
import static ctbrec.sites.streamate.Streamate.NAIAD_URL;

public class StreamateFollowedService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(StreamateFollowedService.class);

    private static final int MODELS_PER_PAGE = 48;
    private final Streamate streamate;
    private final StreamateHttpClient httpClient;
    private final String url;
    private boolean showOnline = true;

    public StreamateFollowedService(Streamate streamate) {
        this.streamate = streamate;
        this.httpClient = streamate.getHttpClient();
        this.url = NAIAD_URL + "/favorites?domain=streamate.com&filters=";
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                try {
                    httpClient.login();
                } catch (Exception e) {
                    LOG.debug("Login was not successful");
                    return Collections.emptyList();
                }
                String saKey = httpClient.getSaKey();
                String pageUrl = url + "&from=" + ((page - 1) * MODELS_PER_PAGE) + "&size=" + MODELS_PER_PAGE;
                LOG.debug("Fetching page {}", pageUrl);
                String smxxx = UUID.randomUUID() + "G0211569057409";
                String smtid = Optional.ofNullable(httpClient.getCookieValue("smtid")).orElse(smxxx);
                String smeid = Optional.ofNullable(httpClient.getCookieValue("smeid")).orElse(smxxx);
                String smvid = Optional.ofNullable(httpClient.getCookieValue("smvid")).orElse(smxxx);
                var request = new Request.Builder()
                        .url(pageUrl)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                        .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                        .header(ORIGIN, streamate.getBaseUrl())
                        .header(REFERER, streamate.getBaseUrl() + "/view/favorites")
                        .header("sakey", saKey)
                        .header("platform", "SCP")
                        .header("smtid", smtid)
                        .header("smeid", smeid)
                        .header("smvid", smvid)
                        .build();
                try (var response = streamate.getHttpClient().execute(request)) {
                    if (response.isSuccessful()) {
                        return parseModels(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }
        };
    }

    private List<Model> parseModels(String content) throws IOException {
        List<Model> models = new ArrayList<>();
        var json = new JSONObject(content);
        if (json.has("totalResultCount")) {
            var performers = json.getJSONArray("performers");
            for (var i = 0; i < performers.length(); i++) {
                var p = performers.getJSONObject(i);
                var nickname = p.getString("nickname");
                StreamateModel model = (StreamateModel) streamate.createModel(nickname);
                model.setId(p.getLong("id"));
                model.setPreview("https://cdn.nsimg.net/snap/320x240/" + model.getId() + ".jpg");
                boolean online = p.optBoolean("online") && notPrivateEtc(p);
                model.setOnline(online);
                model.setOnlineState(online ? ONLINE : OFFLINE);
                if (online == showOnline) {
                    models.add(model);
                }
            }
        } else {
            throw new IOException("Response: " + json.optString("message"));
        }
        return models;
    }

    private boolean notPrivateEtc(JSONObject p) {
        if (p.has("liveState")) {
            var liveState = p.getJSONObject("liveState");
            boolean offline = liveState.optBoolean("onBreak")
                    || liveState.optBoolean("privateChat")
                    || liveState.optBoolean("exclusiveShow")
                    || liveState.optBoolean("specialShow")
                    || liveState.optBoolean("goldShow");
            return !offline;
        }
        return false;
    }

    public void setOnline(boolean online) {
        this.showOnline = online;
    }
}
