package ctbrec.sites.streamate;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.NotImplementedExcetion;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;
import static ctbrec.sites.streamate.StreamateHttpClient.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class StreamateModel extends AbstractModel {

    private static final String ORIGIN = "origin";
    private static final Long MODEL_ID_UNDEFINED = -1L;

    @Setter
    private boolean online = false;
    private final transient List<StreamSource> streamSources = new ArrayList<>();
    private int[] resolution;
    @Getter
    @Setter
    private Long id = MODEL_ID_UNDEFINED;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            String url = "https://sea1c-ls.naiadsystems.com/sea1c-edge-ls/80/live/s:" + getName() + ".json";
            Request req = new Request.Builder().url(url)
                    .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .addHeader(ACCEPT, "*/*")
                    .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .addHeader(REFERER, Streamate.BASE_URL + '/' + getName())
                    .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                    .build();
            try (Response response = site.getHttpClient().execute(req)) {
                online = response.isSuccessful();
            }
        }
        return online;
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (!failFast && (onlineState == UNKNOWN || onlineState == UNCHECKED)) {
            return online ? ONLINE : OFFLINE;
        }
        return onlineState;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        String url = "https://sea1c-ls.naiadsystems.com/sea1c-edge-ls/80/live/s:" + getName() + ".json";
        Request req = new Request.Builder().url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, "*/*")
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, Streamate.BASE_URL + '/' + getName())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                JSONObject json = new JSONObject(body);
                JSONObject formats = json.getJSONObject("formats");
                JSONObject hls = formats.getJSONObject("mp4-hls");

                // add encodings
                JSONArray encodings = hls.getJSONArray("encodings");
                streamSources.clear();
                for (int i = 0; i < encodings.length(); i++) {
                    JSONObject encoding = encodings.getJSONObject(i);
                    StreamSource src = new StreamSource();
                    src.setMediaPlaylistUrl(encoding.getString("location"));
                    src.setWidth(encoding.optInt("videoWidth"));
                    src.setHeight(encoding.optInt("videoHeight"));
                    src.setBandwidth((encoding.optInt("videoKbps") + encoding.optInt("audioKbps")) * 1024);
                    streamSources.add(src);
                }

                // add raw source stream
                if (hls.has(ORIGIN) && !hls.isNull(ORIGIN)) {
                    JSONObject origin = hls.getJSONObject(ORIGIN);
                    StreamSource src = new StreamSource();
                    src.setMediaPlaylistUrl(origin.getString("location"));
                    src.setWidth(origin.optInt("videoWidth"));
                    src.setBandwidth((origin.optInt("videoKbps") + origin.optInt("audioKbps")) * 1024);
                    src.setHeight(StreamSource.ORIGIN);
                    streamSources.add(src);
                }

            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
        return streamSources;
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = null;
    }

    void loadModelId() throws IOException {
        String url = "https://www.streamate.com/api/performer/lookup?nicknames=" + URLEncoder.encode(getName(), UTF_8);
        Request req = new Request.Builder().url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, "*/*")
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(ORIGIN, Streamate.BASE_URL)
                .addHeader(REFERER, Streamate.BASE_URL + '/' + getName())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .addHeader(X_XSRF_TOKEN, ((StreamateHttpClient) site.getHttpClient()).getXsrfToken())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                id = new JSONObject(body).getJSONObject("result").getLong(getName());
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (resolution == null) {
            if (failFast) {
                return new int[2];
            }
            try {
                if (!isOnline()) {
                    return new int[2];
                }
                List<StreamSource> sources = getStreamSources();
                Collections.sort(sources);
                StreamSource best = sources.get(sources.size() - 1);
                if (best.getHeight() == StreamSource.ORIGIN) {
                    best = sources.get(sources.size() - 2);
                }
                resolution = new int[]{best.getWidth(), best.getHeight()};
            } catch (InterruptedException e) {
                log.warn("Couldn't determine stream resolution for {} - {}", getName(), e.getMessage());
                Thread.currentThread().interrupt();
            } catch (ExecutionException | IOException | ParseException | PlaylistException e) {
                log.warn("Couldn't determine stream resolution for {} - {}", getName(), e.getMessage());
            }
        }
        return resolution;
    }

    @Override
    public boolean follow() throws IOException {
        return follow(true);
    }

    @Override
    public boolean unfollow() throws IOException {
        return follow(false);
    }

    private boolean follow(boolean follow) throws IOException {
        StreamateHttpClient client = (StreamateHttpClient) getSite().getHttpClient();
        client.login();
        String saKey = client.getSaKey();
        Long userId = client.getUserId();

        JSONObject requestParams = new JSONObject();
        requestParams.put("sakey", saKey);
        requestParams.put("userid", userId);
        requestParams.put("pid", id);
        requestParams.put("domain", "streamate.com");
        requestParams.put("fav", follow);
        RequestBody body = RequestBody.Companion.create(requestParams.toString(), JSON);

        String url = site.getBaseUrl() + "/ajax/fav-notify.php?userid=" + userId + "&sakey=" + saKey + "&pid=" + id + "&fav=" + follow + "&domain=streamate.com";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, "application/json, */*")
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, getSite().getBaseUrl())
                .post(body)
                .build();
        try (Response response = getSite().getHttpClient().execute(request)) {
            String content = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(content);
                return json.optBoolean("success");
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        id = Long.parseLong(data.get("id"));
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        if (id == null || Objects.equals(id, MODEL_ID_UNDEFINED)) {
            try {
                loadModelId();
            } catch (IOException e) {
                log.error("Couldn't load model ID for {}. This can cause problems with saving / loading the model", getName(), e);
            }
        }
        data.put("id", Long.toString(id));
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        throw new NotImplementedExcetion("Tipping is not implemented for Streamate");
    }
}
