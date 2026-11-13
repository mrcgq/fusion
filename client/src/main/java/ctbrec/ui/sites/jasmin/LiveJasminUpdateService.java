package ctbrec.ui.sites.jasmin;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.sites.jasmin.LiveJasminModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import static ctbrec.io.HttpConstants.*;

public class LiveJasminUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminUpdateService.class);

    private final String url;
    private final LiveJasmin liveJasmin;
    private final int modelsPerPage = 60;

    private String listPageId = "";
    private List<Model> modelsList;
    private int lastPageLoaded;

    private transient Instant lastListInfoRequest = Instant.EPOCH;

    public LiveJasminUpdateService(LiveJasmin liveJasmin, String url) {
        this.liveJasmin = liveJasmin;
        this.url = url;
        this.lastPageLoaded = 0;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                return getModelList().stream()
                        .skip((page - 1) * (long) modelsPerPage)
                        .limit(modelsPerPage)
                        .collect(Collectors.toList()); // NOSONAR
            }
        };
    }

    private List<Model> getModelList() throws IOException {
        page = Math.min(page, 99);
        if ((lastPageLoaded > 0) && Duration.between(lastListInfoRequest, Instant.now()).getSeconds() < 60) {
            while (page > lastPageLoaded) {
                lastPageLoaded++;
                modelsList.addAll(loadMore());
            }
            return modelsList;
        }
        lastPageLoaded = 1;
        modelsList = loadModelList();
        while (page > lastPageLoaded) {
            lastPageLoaded++;
            modelsList.addAll(loadMore());
        }
        if (modelsList == null) {
            return Collections.emptyList();
        }
        return modelsList;
    }

    private List<Model> loadModelList() throws IOException {
        lastListInfoRequest = Instant.now();
        var cookieJar = liveJasmin.getHttpClient().getCookieJar();

        var sortCookie = new Cookie.Builder().domain(LiveJasmin.baseDomain).name("listPageOrderType").value("most_popular").build();
        cookieJar.saveFromResponse(HttpUrl.parse(liveJasmin.getBaseUrl()), Collections.singletonList(sortCookie));

        String category = (url.contains("boys")) ? "boys" : "girls";
        var categoryCookie = new Cookie.Builder().domain(LiveJasmin.baseDomain).name("category").value(category).build();
        cookieJar.saveFromResponse(HttpUrl.parse(liveJasmin.getBaseUrl()), Collections.singletonList(categoryCookie));

        LOG.debug("Fetching page {}", url);
        Request req = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, liveJasmin.getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = liveJasmin.getHttpClient().execute(req)) {
            LOG.debug("Response {} {}", response.code(), response.message());
            if (response.isSuccessful()) {
                String body = response.body().string();
                List<Model> models = new ArrayList<>();
                JSONObject json = new JSONObject(body);
                if (json.optBoolean("success")) {
                    parseModels(models, json);
                } else if (json.optString("error").equals("Please login.")) {
                    var siteUI = SiteUiFactory.getUi(liveJasmin);
                    if (siteUI.login()) {
                        return loadModelList();
                    } else {
                        LOG.error("Request failed:\n{}", body);
                        throw new IOException("Response was not successful");
                    }
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

    private List<Model> loadMore() throws IOException {
        lastListInfoRequest = Instant.now();
        String moreURL = liveJasmin.getBaseUrl() + MessageFormat.format("/en/list-page-ajax/show-more-json/{0}?wide=true&layout=layout-big&_dc={1}", listPageId, String.valueOf(System.currentTimeMillis()));
        LOG.debug("Fetching page {}", moreURL);
        Request req = new Request.Builder()
                .url(moreURL)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, liveJasmin.getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = liveJasmin.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                List<Model> models = new ArrayList<>();
                JSONObject json = new JSONObject(body);
                if (json.optBoolean("success")) {
                    parseModels(models, json);
                }
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void parseModels(List<Model> models, JSONObject json) {
        if (json.has("data")) {
            JSONObject data = json.getJSONObject("data");
            if (data.optInt("isLast") > 0) {
                lastPageLoaded = 999;
            }
            if (data.has("content")) {
                JSONObject content = data.getJSONObject("content");
                if (content.optInt("isLastPage") > 0) {
                    lastPageLoaded = 999;
                }
                listPageId = content.optString("listPageId");
                JSONArray performers = content.getJSONArray("performers");
                for (var i = 0; i < performers.length(); i++) {
                    var m = performers.getJSONObject(i);
                    var name = m.optString("pid");
                    if (name.isEmpty()) {
                        continue;
                    }
                    LiveJasminModel model = (LiveJasminModel) liveJasmin.createModel(name);
                    model.setId(m.getString("id"));
                    model.setPreview(m.optString("profilePictureUrl"));
                    model.setOnlineState(LiveJasminModel.mapStatus(m.optInt("status")));
                    model.setDisplayName(m.optString("display_name", null));
                    models.add(model);
                }
            } // if content
        } // if data
    }
}
