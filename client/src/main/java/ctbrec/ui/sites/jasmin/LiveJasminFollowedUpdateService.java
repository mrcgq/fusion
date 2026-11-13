package ctbrec.ui.sites.jasmin;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.sites.jasmin.LiveJasminModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Future;

import static ctbrec.io.HttpConstants.*;

public class LiveJasminFollowedUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminFollowedUpdateService.class);
    private final LiveJasmin liveJasmin;
    private final String url;
    private boolean showOnline = true;

    public LiveJasminFollowedUpdateService(LiveJasmin liveJasmin) {
        this.liveJasmin = liveJasmin;
        this.url = liveJasmin.getBaseUrl() + "/en/free/favourite/get-favourite-list";
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (!liveJasmin.credentialsAvailable()) {
                    throw new RuntimeException("Credentials missing");
                }

                boolean loggedIn = SiteUiFactory.getUi(liveJasmin).login();
                if (!loggedIn) {
                    throw new RuntimeException("Couldn't login to livejasmin");
                }
                Request request = new Request.Builder()
                        .url(url)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(ACCEPT, "*/*")
                        .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                        .header(REFERER, liveJasmin.getBaseUrl() + "/en/free/favorite")
                        .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                        .build();
                try (Response response = liveJasmin.getHttpClient().execute(request)) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        List<Model> models = new ArrayList<>();
                        JSONObject json = new JSONObject(body);
                        if (json.has("success")) {
                            JSONObject data = json.getJSONObject("data");
                            JSONArray performers = data.getJSONArray("performers");
                            List<Future<?>> loadDetailsFutures = new LinkedList<>();
                            for (int i = 0; i < performers.length(); i++) {
                                JSONObject m = performers.getJSONObject(i);
                                String name = m.optString("pid");
                                if (name.isEmpty()) {
                                    continue;
                                }

                                LiveJasminModel model = (LiveJasminModel) liveJasmin.createModel(name);
                                model.setId(String.valueOf(m.get("id")));
                                model.setDisplayName(m.getString("display_name"));
                                Model.State onlineState = LiveJasminModel.mapStatus(m.getInt("status"));
                                boolean online = onlineState == Model.State.ONLINE;
                                model.setOnlineState(onlineState);
                                if (online == showOnline) {
                                    models.add(model);
                                }
                                loadDetailsFutures.add(GlobalThreadPool.submit(() -> loadModelDetails(model)));
                            }
                            for (Future<?> future : loadDetailsFutures) {
                                try {
                                    future.get();
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                } catch (Exception e) {
                                    // details couldn't be loaded, but that doesn't matter
                                }
                            }
                            LOG.debug("done");
                        } else {
                            LOG.error("Request failed:\n{}", body);
                            throw new IOException("Response was not successful");
                        }
                        return models;
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }

            private void loadModelDetails(LiveJasminModel model) {
                try {
                    String sessionId = liveJasmin.getHttpClient().getCookieJar().getCookie(HttpUrl.parse(liveJasmin.getBaseUrl()), "session").value();
                    String detailsUrl = liveJasmin.getBaseUrl() + "/en/member/flash/get-performer-details/" + model.getName() + "?appletType=html5&noFlash=0&session=" + sessionId;
                    Request request = new Request.Builder()
                            .url(detailsUrl)
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header(ACCEPT, "*/*")
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(REFERER, liveJasmin.getBaseUrl() + "/en/member/chat-html5/" + model.getName())
                            .build();
                    try (Response response = liveJasmin.getHttpClient().execute(request)) {
                        if (response.isSuccessful()) {
                            JSONObject json = new JSONObject(response.body().string());
                            if (json.optBoolean("success")) {
                                JSONObject data = json.getJSONObject("data");
                                model.setPreview(data.getString("profile_picture_url"));
                            }
                        }
                    }
                } catch (IOException e) {
                    // details couldn't be loaded, but that doesn't matter
                }
            }
        };
    }

    public void setShowOnline(boolean showOnline) {
        this.showOnline = showOnline;
    }
}
