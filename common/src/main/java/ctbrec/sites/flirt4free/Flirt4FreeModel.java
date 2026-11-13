package ctbrec.sites.flirt4free;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.StreamInfo;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.ModelOfflineException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ENGLISH;

@Slf4j
public class Flirt4FreeModel extends AbstractModel {
    private static final String KEY_CONFIG = "config";
    private static final String KEY_STATUS = "status";

    @Setter
    private String id;
    @Setter
    private String streamUrl;
    int[] resolution = new int[2];
    private final transient Object monitor = new Object();
    @Getter
    private final transient List<String> categories = new LinkedList<>();
    private boolean isNew = false;
    private String userIdt = "";
    private String userIp = "0.0.0.0";

    private static final Semaphore requestThrottle = new Semaphore(2, true);
    private static volatile long lastRequest = 0;

    private transient JSONObject modelInfo;
    private transient JSONObject stateInfo;
    private transient Instant lastInfoRequest = Instant.EPOCH;
    private transient Instant lastStateRequest = Instant.EPOCH;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            JSONObject info = getStateInfo();
            parseOnlineState(info);
        }
        return onlineState == Model.State.ONLINE;
    }

    private void parseOnlineState(JSONObject json) throws IOException {
        if (json.optString(KEY_STATUS).equals("failed")) {
            if (json.optString("message").equals("Model is inactive")) {
                log.debug("Model inactive or deleted: {}", getName());
                setMarkedForLaterRecording(true);
            }
            onlineState = Model.State.OFFLINE;
            return;
        }
        int modelId = json.optInt("model_id");
        if (modelId > 0) {
            id = String.valueOf(modelId);
        }
        if (json.optString(KEY_STATUS).equals("online") && modelId > 0) {
            getModelInfo();
        } else {
            onlineState = Model.State.OFFLINE;
        }
    }

    private JSONObject getStateInfo() throws IOException {
        if (Objects.nonNull(stateInfo) && Duration.between(lastStateRequest, Instant.now()).getSeconds() < 5) {
            return stateInfo;
        }
        lastStateRequest = Instant.now();
        stateInfo = loadStateInfo();
        return stateInfo;
    }

    private JSONObject loadStateInfo() throws IOException {
        String url = HTTPS + "://ws.vs3.com/rooms/check-model-status.php?model_name=" + getName();
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, ENGLISH.getLanguage())
                .header(REFERER, getUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = getSite().getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                return json;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private JSONObject getModelInfo() throws IOException {
        if (Objects.nonNull(modelInfo) && Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            return modelInfo;
        }
        lastInfoRequest = Instant.now();
        modelInfo = loadModelInfo();
        return modelInfo;
    }

    private JSONObject loadModelInfo() throws IOException {
        String url = getSite().getBaseUrl() + "/webservices/chat-room-interface.php?a=login_room&model_id=" + id;
        log.trace("Loading url {}", url);
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, ENGLISH.getLanguage())
                .header(REFERER, getUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = getSite().getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                if (json.optString(KEY_STATUS).equals("success")) {
                    JSONObject config = json.getJSONObject(KEY_CONFIG);
                    setDisplayName(config.getJSONObject("performer").optString("name", getName()));
                    userIp = config.getJSONObject("user").getString("ip");
                    onlineState = mapStatus(config.getJSONObject("room").optString(KEY_STATUS));
                } else {
                    log.trace("Loading model info failed. Assuming model {} is offline", getName());
                    onlineState = Model.State.OFFLINE;
                }
                return json;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private State mapStatus(String status) {
        return switch (status) {
            case "P", "F" -> State.PRIVATE;
            case "A" -> State.AWAY;
            case "O" -> State.ONLINE;
            default -> State.UNKNOWN;
        };
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        MasterPlaylist masterPlaylist;
        try {
            acquireSlot();
            try {
                loadStreamUrl();
            } finally {
                releaseSlot();
            }
            masterPlaylist = getMasterPlaylist();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e);
        }
        List<StreamSource> sources = new ArrayList<>();
        for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
            if (playlist.hasStreamInfo()) {
                StreamSource src = new StreamSource();
                StreamInfo info = playlist.getStreamInfo();
                src.setBandwidth(info.getBandwidth());
                src.setHeight((info.hasResolution()) ? info.getResolution().height : 0);
                src.setWidth((info.hasResolution()) ? info.getResolution().width : 0);
                HttpUrl masterPlaylistUrl = HttpUrl.parse(streamUrl);
                src.setMediaPlaylistUrl(HTTPS + "://" + masterPlaylistUrl.host() + '/' + playlist.getUri());
                log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    public MasterPlaylist getMasterPlaylist() throws IOException, ParseException, PlaylistException, InterruptedException {
        log.trace("Loading master playlist {}", streamUrl);
        Request req = new Request.Builder()
                .url(streamUrl)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, ENGLISH.getLanguage())
                .header(REFERER, getUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        acquireSlot();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                InputStream inputStream = response.body().byteStream();
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                MasterPlaylist master = playlist.getMasterPlaylist();
                return master;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        } finally {
            releaseSlot();
        }
    }

    private void loadStreamUrl() throws InterruptedException {
        try {
            JSONObject info = getModelInfo();
            streamUrl = "";

            String chatHost = info.getJSONObject(KEY_CONFIG).getJSONObject("room").getString("host").replace("chat", "chat-vip");
            String chatPort = info.getJSONObject(KEY_CONFIG).getJSONObject("room").getString("port");
            String chatToken = info.getString("token_enc");
            String url = HTTPS + "://" + chatHost + "/chat?token=" + URLEncoder.encode(chatToken, UTF_8) + "&port_to_be=" + chatPort;

            log.trace("Opening chat websocket {}", url);
            Request req = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, "*/*")
                    .header(ACCEPT_LANGUAGE, ENGLISH.getLanguage())
                    .header(REFERER, getUrl())
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                    .build();

            getSite().getHttpClient().newWebSocket(req, new WebSocketListener() {
                @Override
                public void onMessage(WebSocket webSocket, String text) {
                    log.trace("Chat websocket for {}: {}", getName(), text);
                    JSONObject json = new JSONObject(text);
                    if (json.optString("command").equals("8011")) {
                        try {
                            JSONObject data = json.getJSONObject("data");
                            JSONObject stream = data.getJSONObject("video_info")
                                    .getJSONObject("hls")
                                    .getJSONArray("providers")
                                    .getJSONObject(0);
                            String streamHost = stream.optString("stream_host", "hls.vscdns.com");
                            String streamName = stream.optString("stream_name", "manifest.m3u8");
                            String streamKey = stream.optString("stream_key", "&");
                            if (!streamKey.startsWith("&")) {
                                streamUrl = MessageFormat.format(HTTPS + "://{0}/{1}?key={2}", streamHost, streamName, streamKey);
                            }
                            onlineState = mapStatus(data.optString("room_state"));
                            resolution[0] = Integer.parseInt(json.optString("stream_width", "0"));
                            resolution[1] = Integer.parseInt(json.optString("stream_height", "0"));
                        } catch (Exception e) {
                            log.trace("Can not get stream info from WS", e);
                        } finally {
                            webSocket.close(1000, "");
                        }
                    }
                }

                @Override
                public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                    log.trace("Chat websocket for {} failed", getName(), t);
                    if (response != null) {
                        response.close();
                    }
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                }

                @Override
                public void onClosed(WebSocket webSocket, int code, String reason) {
                    log.trace("Chat websocket for {} closed {} {}", getName(), code, reason);
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                }
            });

            synchronized (monitor) {
                monitor.wait(10_000);
                if (StringUtil.isBlank(streamUrl)) {
                    if (isOnline(false)) {
                        String cdn = info.getJSONObject(KEY_CONFIG).getJSONObject("env").getString("cdn").split("\\.")[0].replace(HTTPS + "://", "");
                        streamUrl = MessageFormat.format(HTTPS + "://hls.vscdns.com/manifest.m3u8?key=nil&provider={0}&model_id={1}", cdn, id);
                    } else {
                        throw new ModelOfflineException(this);
                    }
                }
                log.debug("Stream URL is {}", streamUrl);
            }

        } catch (InterruptedException e) {
            log.error("Interrupted while loading stream url");
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException("Couldn't determine stream URL for model " + getName());
        }
    }

    @Override
    public void invalidateCacheEntries() {
        stateInfo = null;
        modelInfo = null;
        lastInfoRequest = Instant.EPOCH;
        lastStateRequest = Instant.EPOCH;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        try {
            // make sure we are logged in and all necessary model data is available
            getSite().login();
            fetchStreamUrl();

            // send the tip
            int giftId = 171;
            int amount = tokens.intValue();
            log.debug("Sending tip of {} to {}", amount, getName());
            String url = HTTPS + "://ws.vs3.com/rooms/send-tip.php?" +
                    "gift_id=" + giftId +
                    "&num_credits=" + amount +
                    "&userId=" + getUserIdt() +
                    "&username=" + Config.getInstance().getSettings().flirt4freeUsername +
                    "&userIP=" + userIp +
                    "&anonymous=N&response_type=json" +
                    "&t=" + System.currentTimeMillis();
            log.debug("Trying to send tip: {}", url);
            Request req = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, "*/*")
                    .header(ACCEPT_LANGUAGE, ENGLISH.getLanguage())
                    .header(REFERER, getUrl())
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(REFERER, getUrl())
                    .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                    .build();
            try (Response response = getSite().getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    JSONObject json = new JSONObject(response.body().string());
                    if (json.optInt("success") != 1) {
                        String msg = json.optString("message");
                        if (json.has("error_message")) {
                            msg = json.getString("error_message");
                        }
                        log.error("Sending tip failed: {}", msg);
                        log.debug("Response: {}", json.toString(2));
                        throw new IOException(msg);
                    }
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't acquire request slot", e);
        }
    }

    private void fetchStreamUrl() throws InterruptedException, IOException {
        acquireSlot();
        try {
            loadStreamUrl();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't send tip", e);
        } finally {
            releaseSlot();
        }
    }

    private String getUserIdt() throws IOException, InterruptedException {
        if (userIdt.isEmpty()) {
            acquireSlot();
            try {
                Request req = new Request.Builder()
                        .url(getUrl())
                        .header(ACCEPT, "*/*")
                        .header(ACCEPT_LANGUAGE, "en-US,en;q=0.5")
                        .header(REFERER, getUrl())
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .build();
                try (Response response = getSite().getHttpClient().execute(req)) {
                    if (response.isSuccessful()) {
                        String body = response.body().string();
                        Matcher m = Pattern.compile("idt\\s*:\\s*'(.*?)',").matcher(body);
                        if (m.find()) {
                            userIdt = m.group(1);
                        } else {
                            throw new IOException("userIdt not found on HTML page");
                        }
                    } else {
                        throw new HttpException(response.code(), response.message());
                    }
                }
            } finally {
                releaseSlot();
            }
        }
        return userIdt;
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (!failFast && streamUrl != null && resolution[0] == 0) {
            try {
                List<StreamSource> streamSources = getStreamSources();
                Collections.sort(streamSources);
                StreamSource best = streamSources.getLast();
                resolution = new int[]{best.getHeight(), best.getHeight()};
            } catch (IOException | ParseException | PlaylistException e) {
                throw new ExecutionException("Couldn't determine stream resolution", e);
            }
        }
        return resolution;
    }

    public void setStreamResolution(int[] res) {
        this.resolution = res;
    }

    @Override
    public boolean follow() throws IOException {
        try {
            return changeFavoriteStatus(true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't follow model " + getName(), e);
        }
    }

    @Override
    public boolean unfollow() throws IOException {
        try {
            isOnline(true);
            return changeFavoriteStatus(false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't unfollow model " + getName(), e);
        } catch (ExecutionException e) {
            throw new IOException("Couldn't unfollow model " + getName(), e);
        }
    }

    private boolean changeFavoriteStatus(boolean add) throws IOException, InterruptedException {
        getSite().login();
        acquireSlot();
        try {
            getModelInfo();
        } finally {
            releaseSlot();
        }
        String url = getSite().getBaseUrl() + "/external.php?a=" +
                (add ? "add_favorite" : "delete_favorite") +
                "&id=" + id +
                "&name=" + getName() +
                "&t=" + System.currentTimeMillis();
        log.debug("Sending follow/unfollow request: {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "en-US,en;q=0.5")
                .header(REFERER, getUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                log.debug("Follow/Unfollow response: {}", body);
                return Objects.equals(body, "1");
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        id = data.get("id");
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        data.put("id", id);
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }

    @Override
    public String getName() {
        String original = super.getName();
        String fixed = original.toLowerCase().replace(" ", "-").replace("_", "-");
        if (!fixed.equals(original)) {
            setName(fixed);
        }
        return fixed;
    }

    @Override
    public Instant getLastSeen() {
        Instant lastSeen = super.getLastSeen();
        return (lastSeen.equals(Instant.EPOCH)) ? getAddedTimestamp() : lastSeen;
    }

    private void acquireSlot() throws InterruptedException {
        requestThrottle.acquire();
        long now = System.currentTimeMillis();
        long millisSinceLastRequest = now - lastRequest;
        if (millisSinceLastRequest < 500) {
            Thread.sleep(500 - millisSinceLastRequest);
        }
    }

    private void releaseSlot() {
        lastRequest = System.currentTimeMillis();
        requestThrottle.release();
    }
}
