package ctbrec.sites.bonga;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;

@Slf4j
public class BongaCamsModel extends AbstractModel {

    private static final String ARGS = "args[]";
    private static final String SUCCESS = "success";
    private static final String STATUS = "status";

    @Setter
    private boolean online = false;
    private final transient List<StreamSource> streamSources = new ArrayList<>();
    private int[] resolution;

    private transient JSONObject modelInfo;
    private transient Instant lastInfoRequest = Instant.EPOCH;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            return completeOnlineCheck();
        }
        return online;
    }

    private boolean completeOnlineCheck() throws IOException {
        JSONObject roomData = getRoomData();
        if (!roomData.has("performerData")) {
            onlineState = UNKNOWN;
            return false;
        }
        JSONObject performerData = roomData.getJSONObject("performerData");
        setDisplayName(performerData.optString("displayName"));
        String chatType = performerData.optString("showType");
        boolean isAway = performerData.optBoolean("isAway");
        
        // looks like isOnline key is new. Treat it's absence as true (old behavior)
        boolean jsonIsOnline = performerData.optBoolean("isOnline", true);

        if (!jsonIsOnline) {
            onlineState = OFFLINE;
            online = false;
        } else {
            onlineState = mapState(chatType);
            if (onlineState == ONLINE) {
                if (isStreamAvailable()) {
                    if (isAway) {
                        onlineState = AWAY;
                        online = false;
                    } else {
                        online = true;
                    }
                } else {
                    online = false;
                    onlineState = AWAY;
                }
            } else {
                online = false;
            }
        }
        return online;
    }

    public State mapState(String roomState) {
        return switch (roomState) {
            case "private", "fullprivate" -> PRIVATE;
            case "group" -> GROUP;
            case "public" -> ONLINE;
            default -> {
                log.debug(roomState);
                yield OFFLINE;
            }
        };
    }

    private boolean isStreamAvailable() {
        try {
            String url = getStreamUrl();
            Request req = new Request.Builder().url(url).build();
            try (Response resp = site.getHttpClient().execute(req)) {
                if (resp.isSuccessful()) {
                    String body = Objects.requireNonNull(resp.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                    return body.contains("#EXT-X-STREAM-INF");
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            log.warn("Couldn't check if stream is available: {}", e.getLocalizedMessage());
            return false;
        }
    }

    private JSONObject getRoomData() throws IOException {
        if (Objects.nonNull(modelInfo) && Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            return modelInfo;
        }
        lastInfoRequest = Instant.now();
        String url = getSite().getBaseUrl() + "/tools/amf.php";
        RequestBody body = new FormBody.Builder()
                .add("method", "getRoomData")
                .add(ARGS, getName())
                .add(ARGS, "false")
                .build();
        Request request = newRequestBuilder()
                .url(url)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .post(body)
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                modelInfo = json;
                return json;
            } else {
                throw new IOException(response.code() + " " + response.message());
            }
        }
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (!failFast) {
            try {
                isOnline(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                onlineState = OFFLINE;
            } catch (IOException | ExecutionException e) {
                onlineState = OFFLINE;
            }
        }
        return onlineState;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        String streamUrl = getStreamUrl();
        Request req = newRequestBuilder().url(streamUrl).build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                InputStream inputStream = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).byteStream();
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                MasterPlaylist master = playlist.getMasterPlaylist();
                extractStreamSources(streamUrl, master);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
        return streamSources;
    }

    private void extractStreamSources(String streamUrl, MasterPlaylist master) {
        streamSources.clear();
        for (PlaylistData playlist : master.getPlaylists()) {
            StreamSource src = new StreamSource();
            src.setMediaPlaylistUrl(streamUrl.replace("playlist.m3u8", playlist.getUri()));
            if (playlist.hasStreamInfo()) {
                src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                src.setHeight(Optional.ofNullable(playlist.getStreamInfo().getResolution()).map(res -> res.height).orElse(0));
                src.setWidth(Optional.ofNullable(playlist.getStreamInfo().getResolution()).map(res -> res.width).orElse(0));
            } else {
                src.setBandwidth(0);
                src.setWidth(0);
                src.setHeight(0);
            }
            streamSources.add(src);
        }
    }

    private String getStreamUrl() throws IOException {
        JSONObject roomData = getRoomData();
        if (roomData.optString(STATUS).equals(SUCCESS)) {
            JSONObject localData = roomData.getJSONObject("localData");
            String server = localData.getString("videoServerUrl");
            return "https:" + server + "/hls/stream_" + getName() + "/playlist.m3u8";
        } else {
            throw new IOException("Request was not successful: " + roomData.toString(2));
        }
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = null;
        modelInfo = null;
        lastInfoRequest = Instant.EPOCH;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        String url = getSite().getBaseUrl() + "/chat-ajax-amf-service?" + System.currentTimeMillis();
        int userId = ((BongaCamsHttpClient) site.getHttpClient()).getUserId();
        RequestBody body = new FormBody.Builder()
                .add("method", "tipModel")
                .add(ARGS, getName())
                .add(ARGS, Integer.toString(tokens.intValue()))
                .add(ARGS, Integer.toString(userId))
                .add("args[3]", "")
                .build();
        Request request = newRequestBuilder()
                .url(url)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(REFERER, getSite().getBaseUrl() + '/' + getName())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .post(body)
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                if (!json.optString(STATUS).equals(SUCCESS)) {
                    log.error("Sending tip failed {}", json.toString(2));
                    throw new IOException("Sending tip failed");
                }
            } else {
                throw new IOException(response.code() + ' ' + response.message());
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
                StreamSource best = sources.getLast();
                resolution = new int[]{best.getWidth(), best.getHeight()};
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Couldn't determine stream resolution for {} - {}", getName(), e.getMessage());
                resolution = new int[2];
            } catch (ExecutionException | IOException | ParseException | PlaylistException e) {
                log.warn("Couldn't determine stream resolution for {} - {}", getName(), e.getMessage());
                resolution = new int[2];
            }
        }
        return resolution;
    }

    @Override
    public boolean follow() throws IOException {
        if (!getSite().login()) {
            throw new IOException("Not logged in");
        }

        String url = getSite().getBaseUrl() + "/follow/" + getName();
        log.debug("Calling {}", url);
        RequestBody body = new FormBody.Builder()
                .add("src", "public-chat")
                .add("_csrf_token", getCsrfToken())
                .build();
        Request req = newRequestBuilder()
                .url(url)
                .method("POST", body)
                .header(REFERER, getUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String msg = Objects.requireNonNull(resp.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                JSONObject json = new JSONObject(msg);
                if (json.optBoolean(SUCCESS)) {
                    log.debug("Follow/Unfollow -> {}", msg);
                    return true;
                } else {
                    log.debug(msg);
                    throw new IOException("Response was " + msg);
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    private String getCsrfToken() throws IOException {
        Request req = newRequestBuilder().url(getUrl()).build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String content = Objects.requireNonNull(resp.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                Element html = HtmlParser.getTag(content, "html");
                String csrfToken = html.attr("data-csrf_value");
                log.debug("CSRF-Token {}", csrfToken);
                return csrfToken;
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    @Override
    public boolean unfollow() throws IOException {
        if (!getSite().login()) {
            throw new IOException("Not logged in");
        }

        String url = getSite().getBaseUrl() + "/unfollow/" + getName();
        log.debug("Calling {}", url);
        RequestBody body = new FormBody.Builder()
                .add("_csrf_token", getCsrfToken())
                .build();
        Request req = newRequestBuilder()
                .url(url)
                .method("POST", body)
                .header(REFERER, getUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String msg = Objects.requireNonNull(resp.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                JSONObject json = new JSONObject(msg);
                if (json.optBoolean(SUCCESS)) {
                    log.debug("Follow/Unfollow -> {}", msg);
                    return true;
                } else {
                    log.debug(msg);
                    throw new IOException("Response was " + msg);
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    public void mapOnlineState(String roomState) {
        switch (roomState) {
            case "private", "fullprivate" -> setOnlineState(PRIVATE);
            case "group", "public" -> {
                setOnlineState(ONLINE);
                setOnline(true);
            }
            default -> {
                log.debug(roomState);
                setOnlineState(OFFLINE);
            }
        }
    }

    @Override
    public boolean exists() throws IOException {
        Request req = newRequestBuilder().url(getUrl()).build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (!response.isSuccessful() && response.code() == 404) {
                return false;
            } else {
                return !response.request().url().toString().endsWith("_deleted");
            }
        }
    }

    @Override
    public Instant getLastSeen() {
        Instant lastSeen = super.getLastSeen();
        return (lastSeen.equals(Instant.EPOCH)) ? getAddedTimestamp() : lastSeen;
    }

    private Request.Builder newRequestBuilder() {
        return new Request.Builder()
                .url(getUrl())
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getSite().getBaseUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent);
    }
}
