package ctbrec.ui.sites.streamate;

import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.streamate.Streamate;
import ctbrec.sites.streamate.StreamateHttpClient;
import ctbrec.sites.streamate.StreamateModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.ORIGIN;

public class StreamateUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(StreamateUpdateService.class);

    private static final int MODELS_PER_PAGE = 48;
    private final Streamate streamate;
    private final StreamateHttpClient httpClient;
    private String url;

    public StreamateUpdateService(Streamate streamate, String url) {
        this.streamate = streamate;
        this.url = url;
        this.httpClient = streamate.getHttpClient();
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                int from = (page - 1) * MODELS_PER_PAGE;
                String saKey = httpClient.getSaKey();
                String pageUrl = url + "&from=" + from + "&size=" + MODELS_PER_PAGE;
                LOG.debug("Fetching page {}", pageUrl);
                String smxxx = UUID.randomUUID() + "G0211569057409";
                String smtid = Optional.ofNullable(httpClient.getCookieValue("smtid")).orElse(smxxx);
                String smeid = Optional.ofNullable(httpClient.getCookieValue("smeid")).orElse(smxxx);
                String smvid = Optional.ofNullable(httpClient.getCookieValue("smvid")).orElse(smxxx);

                var request = httpClient.newRequestBuilder()
                        .url(pageUrl)
                        .header(ORIGIN, streamate.getBaseUrl())
                        .header("sakey", saKey)
                        .header("platform", "SCP")
                        .header("smtid", smtid)
                        .header("smeid", smeid)
                        .header("smvid", smvid)
                        .build();
                try (var response = httpClient.execute(request)) {
                    if (response.isSuccessful()) {
                        return parseModels(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }
        };
    }

    private List<Model> parseModels(String content) {
        List<Model> models = new ArrayList<>();
        var json = new JSONObject(content);
        var performers = json.getJSONArray("performers");
        for (var i = 0; i < performers.length(); i++) {
            var p = performers.getJSONObject(i);
            var nickname = p.getString("nickname");
            StreamateModel model = (StreamateModel) streamate.createModel(nickname);
            model.setId(p.getLong("id"));
            model.setPreview("https://cdn.nsimg.net/snap/320x240/" + model.getId() + ".jpg");
            model.setDescription(p.optString("headlineMessage"));
            var online = p.optBoolean("online");
            model.setOnline(online);
            model.setOnlineState(online ? ONLINE : OFFLINE);
            // TODO figure out, what all the states mean
            // liveState {…}
            // exclusiveShow false
            // goldShow true
            // onBreak false
            // partyChat true
            // preGoldShow true
            // privateChat false
            // specialShow false
            if (p.optBoolean("onBreak")) {
                model.setOnlineState(AWAY);
            } else if (p.optBoolean("goldShow") || p.optBoolean("privateChat") || p.optBoolean("exclusiveShow")) {
                model.setOnlineState(PRIVATE);
            }
            models.add(model);
        }
        return models;
    }

    public void setOnline(boolean online) {
        if (online) {
            url = url.replace("online:false", "online:true");
        } else {
            url = url.replace("online:true", "online:false");
        }
    }
}
