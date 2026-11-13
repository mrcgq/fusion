package ctbrec.ui.sites.fc2live;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONObject;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.sites.fc2live.Fc2Model;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Fc2FollowedUpdateService extends PaginatedScheduledService {

    private Fc2Live fc2live;

    public Fc2FollowedUpdateService(Fc2Live fc2live) {
        this.fc2live = fc2live;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                return loadModels();
            }
        };
    }

    private List<Model> loadModels() throws IOException {
        if(!fc2live.login()) {
            throw new IOException("Login didn't work");
        }

        RequestBody body = new FormBody.Builder()
                .add("mode", "list")
                .add("page", Integer.toString(page - 1))
                .build();
        Request req = new Request.Builder()
                .url(fc2live.getBaseUrl() + "/api/favoriteManager.php")
                .header(REFERER, fc2live.getBaseUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build();
        try (Response resp = fc2live.getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                List<Model> models = new ArrayList<>();
                var content = resp.body().string();
                var json = new JSONObject(content);
                if (json.optInt("status") == 1) {
                    var data = json.getJSONArray("data");
                    for (var i = 0; i < data.length(); i++) {
                        var m = data.getJSONObject(i);
                        Fc2Model model = (Fc2Model) fc2live.createModel(m.getString("name"));
                        model.setId(m.getString("id"));
                        model.setUrl(Fc2Live.BASE_URL + '/' + model.getId());
                        var previewUrl = m.optString("icon");
                        if (previewUrl == null || previewUrl.trim().isEmpty()) {
                            previewUrl = "https://live-storage.fc2.com/thumb/" + model.getId() + "/thumb.jpg";
                        }
                        model.setPreview(previewUrl);
                        model.setDescription("");
                        models.add(model);
                    }
                    return models;
                } else {
                    throw new IOException("Request was not successful: " + json.toString());
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    public void setShowOnline(boolean yes) {
        // not implemented yet
    }
}
