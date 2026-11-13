package ctbrec.ui.sites.showup;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.json.JSONObject;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.UnexpectedResponseException;
import ctbrec.io.HttpException;
import ctbrec.sites.showup.Showup;
import ctbrec.sites.showup.ShowupModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;

public class ShowupFollowedUpdateService extends PaginatedScheduledService {

    private Showup site;
    private boolean showOnline = true;

    public ShowupFollowedUpdateService(Showup site) {
        this.site = site;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                SiteUiFactory.getUi(site).login();

                var request = new Request.Builder()
                        .url(site.getBaseUrl() + "/site/favorites")
                        .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                        .build();
                try (var response = site.getHttpClient().execute(request)) {
                    if (response.isSuccessful()) {
                        var body = response.body().string();
                        var json = new JSONObject(body);
                        if (json.optString("status").equalsIgnoreCase("success")) {
                            Map<String, String> onlineModels = parseOnlineModels(json);
                            return parseFavorites(json).stream()
                                    .filter(m -> onlineModels.containsKey(m.getUid()) == showOnline)
                                    .collect(Collectors.toList());
                        } else {
                            throw new UnexpectedResponseException("Request was not successful: " + body);
                        }
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }

            private List<ShowupModel> parseFavorites(JSONObject json) {
                var favorites = new ArrayList<ShowupModel>();
                var list = json.getJSONArray("list");
                for (var i = 0; i < list.length(); i++) {
                    var m = list.getJSONObject(i);
                    var model = new ShowupModel();
                    model.setSite(site);
                    model.setUid(m.optString("fav_uid"));
                    model.setName(m.optString("username"));
                    model.setUrl(site.getBaseUrl() + '/' + model.getName());
                    favorites.add(model);
                }
                return favorites;
            }

            private Map<String, String> parseOnlineModels(JSONObject json) {
                var onlineModels = new HashMap<String, String>();
                var online = json.getJSONArray("online");
                for (var i = 0; i < online.length(); i++) {
                    var m = online.getJSONObject(i);
                    String preview = site.getBaseUrl() + "/files/" + m.optString("big_img") + ".jpg";
                    onlineModels.put(String.valueOf(m.optLong("uid")), preview);
                }
                return onlineModels;
            }
        };
    }

    void showOnline(boolean online) {
        this.showOnline = online;
    }
}
