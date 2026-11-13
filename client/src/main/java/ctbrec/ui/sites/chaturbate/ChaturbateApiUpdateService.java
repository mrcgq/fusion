package ctbrec.ui.sites.chaturbate;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.chaturbate.ChaturbateModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import static ctbrec.io.HttpConstants.*;


@Slf4j
@RequiredArgsConstructor
public class ChaturbateApiUpdateService extends PaginatedScheduledService {

    private final String url;
    private final Chaturbate chaturbate;

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            protected List<Model> call() throws Exception {
                var request = new Request.Builder()
                        .url(url)
                        .header(USER_AGENT, chaturbate.getHttpClient().getEffectiveUserAgent())
                        .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                        .build();
                try (var response = chaturbate.getHttpClient().execute(request)) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        log.trace(body);
                        var models = new ArrayList<Model>();
                        JSONObject json = new JSONObject(body);
                        JSONArray rooms = json.optJSONArray("rooms");
                        for (int i = 0; rooms != null && i < rooms.length(); i++) {
                            JSONObject room = rooms.getJSONObject(i);
                            String name = room.getString("room");
                            ChaturbateModel model = (ChaturbateModel) chaturbate.createModel(name);
                            model.setDescription(toText(room.optString("subject")) + " #gender:" + room.optString("gender"));
                            if (StringUtil.isNotBlank(room.optString("display_age"))) {
                                model.setDescription(model.getDescription() + " #age:" + room.optString("display_age"));
                            }
                            models.add(model);
                        }
                        return models;
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }
        };
    }

    protected String toText(String html) {
        return HtmlParser.getText(html, "*");
    }
}
