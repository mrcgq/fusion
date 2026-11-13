package ctbrec.ui.sites.cherrytv;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpException;
import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.sites.cherrytv.CherryTvModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.ACCEPT_LANGUAGE;
import static ctbrec.io.HttpConstants.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;

public class CherryTvUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(CherryTvUpdateService.class);
    protected static final long MODELS_PER_PAGE = 50;

    protected String url;
    private final boolean loginRequired;
    protected final CherryTv site;
    private Predicate<Model> filter;

    public CherryTvUpdateService(String slug, CherryTv site, boolean loginRequired) {
        this.site = site;
        this.url = "https://api.cherry.tv/graphql?query=" + URLEncoder.encode(BROADCASTS_QUERY
                .replace(" ", "")
                .replace("${slug}", slug), UTF_8);
        this.loginRequired = loginRequired;

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName("CherryTvUpdateService");
            return t;
        });
        setExecutor(executor);
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (loginRequired && !site.getHttpClient().login()) {
                    throw new IOException("Login failed");
                }

                LOG.debug("Fetching page {}", url);

                var request = new Request.Builder()
                        .url(url)
                        .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .build();
                try (var response = site.getHttpClient().execute(request)) {
                    if (response.isSuccessful()) {
                        String body = Objects.requireNonNull(response.body()).string();
                        Stream<Model> stream = parseModels(body).stream();
                        if (filter != null) {
                            stream = stream.filter(filter);
                        }
                        return stream.skip((page - 1) * MODELS_PER_PAGE)
                                .limit(MODELS_PER_PAGE)
                                .collect(Collectors.toList());
                    } else {
                        LOG.debug(Objects.requireNonNull(response.body()).string());
                        throw new HttpException(response.code(), response.message());
                    }
                }
            }
        };
    }

    protected List<Model> parseModels(String body) throws IOException {
        var json = new JSONObject(body);
        if (json.has("errors")) {
            JSONArray errors = json.getJSONArray("errors");
            JSONObject first = errors.getJSONObject(0);
            throw new IOException(first.getString("message"));
        }
        List<Model> models = new ArrayList<>();
        try {
            JSONArray broadcasts = json.getJSONObject("data").getJSONObject("broadcastsPaged").getJSONArray("broadcasts");
            for (int i = 0; i < broadcasts.length(); i++) {
                JSONObject broadcast = broadcasts.getJSONObject(i);
                CherryTvModel model = site.createModel(broadcast.optString("username"));
                model.setDisplayName(broadcast.optString("title"));
                model.setDescription(broadcast.optString("description"));
                model.setPreview(broadcast.optString("thumbnailUrl"));
                var online = broadcast.optString("showStatus").equalsIgnoreCase("Public")
                        && broadcast.optString("broadcastStatus").equalsIgnoreCase("Live");
                model.setOnline(online);
                model.setOnlineState(online ? ONLINE : OFFLINE);
                JSONArray tags = broadcast.optJSONArray("tags");
                if (tags != null) {
                    for (int j = 0; j < tags.length(); j++) {
                        model.getTags().add(tags.getString(j));
                    }
                }
                models.add(model);
            }
        } catch (JSONException e) {
            LOG.error("Couldn't parse JSON, the structure might have changed", e);
        }
        return models;
    }

    public void setFilter(Predicate<Model> filter) {
        this.filter = filter;
    }

    private static final String BROADCASTS_QUERY = """
            {
                broadcastsPaged(query: {limit:1000,slug:"${slug}"}) {
                    broadcasts {
                        id
                        title
                        username
                        description
                        thumbnailUrl
                        tags
                        broadcastStatus
                        showStatus
                    }
                    totalCount
                }
            }
            """;
}
