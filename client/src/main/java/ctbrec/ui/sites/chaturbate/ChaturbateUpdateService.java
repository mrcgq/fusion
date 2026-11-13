package ctbrec.ui.sites.chaturbate;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.chaturbate.ChaturbateModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;
import org.jsoup.Jsoup;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static ctbrec.io.HttpConstants.*;

@Slf4j
public class ChaturbateUpdateService extends PaginatedScheduledService {

    private String url;
    private final boolean loginRequired;
    private final Chaturbate chaturbate;

    private static final int PAGE_SIZE = 90;

    public ChaturbateUpdateService(String url, boolean loginRequired, Chaturbate chaturbate) {
        this.url = url;
        this.loginRequired = loginRequired;
        this.chaturbate = chaturbate;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (loginRequired && !chaturbate.credentialsAvailable()) {
                    return Collections.emptyList();
                } else {
                    String pageUrl = ChaturbateUpdateService.this.url + "&limit=" + PAGE_SIZE + "&offset=" + (page - 1) * PAGE_SIZE;
                    log.debug("Fetching page {}", pageUrl);
                    if (loginRequired) {
                        SiteUiFactory.getUi(chaturbate).login();
                    }
                    Request request = new Request.Builder()
                            .url(pageUrl)
                            .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                            .header(USER_AGENT, chaturbate.getHttpClient().getEffectiveUserAgent())
                            .build();
                    try (var response = chaturbate.getHttpClient().execute(request)) {
                        if (response.isSuccessful()) {
                            String body = response.body().string();
                            List<Model> models = new ArrayList<>();
                            JSONObject json = new JSONObject(body);
                            if (json.has("rooms")) {
                                JSONArray rooms = json.optJSONArray("rooms");
                                for (int i = 0; i < rooms.length(); i++) {
                                    JSONObject room = rooms.getJSONObject(i);
                                    ChaturbateModel model = (ChaturbateModel) chaturbate.createModel(room.getString("username"));
                                    if (room.has("subject")) {
                                        model.setDescription(Jsoup.parse(room.getString("subject")).text());
                                    }
                                    models.add(model);
                                }
                            }
                            return models;
                        } else {
                            int code = response.code();
                            throw new IOException("HTTP status " + code);
                        }
                    }
                }
            }
        };
    }

    public void setUrl(String url) {
        this.url = url;
    }

}
