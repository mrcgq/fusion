package ctbrec.sites.fc2live;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.StreamInfo;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

import static ctbrec.io.HttpConstants.*;

@Slf4j
public class Fc2Model extends AbstractModel {
    @Getter
    @Setter
    private String id;
    @Getter
    @Setter
    private int viewerCount;
    private boolean online;
    private String version;
    private String playlistUrl;
    private transient WebSocket ws;
    private final transient AtomicInteger websocketUsage = new AtomicInteger(0);
    private long lastHeartBeat = System.currentTimeMillis();
    private int messageId = 1;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            loadModelInfo();
        }
        return online;
    }

    void loadModelInfo() throws IOException {
        String url = Fc2Live.BASE_URL + "/api/memberApi.php";
        RequestBody body = new FormBody.Builder()
                .add("channel", "1")
                .add("profile", "1")
                .add("streamid", id)
                .build();
        Request req = new Request.Builder()
                .url(url)
                .method("POST", body)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, Fc2Live.BASE_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response resp = getSite().getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String msg = resp.body().string();
                JSONObject json = new JSONObject(msg);
                // log.debug(json.toString(2));
                JSONObject data = json.getJSONObject("data");
                JSONObject channelData = data.getJSONObject("channel_data");
                online = channelData.optInt("is_publish") == 1;
                onlineState = online ? State.ONLINE : State.OFFLINE;
                if (channelData.optInt("fee") == 1) {
                    onlineState = State.PRIVATE;
                    online = false;
                }
                version = channelData.optString("version");
                if (data.has("profile_data")) {
                    JSONObject profileData = data.getJSONObject("profile_data");
                    setName(profileData.getString("name").replace('/', '_'));
                }
            } else {
                throw new IOException("HTTP status " + resp.code() + " " + resp.message());
            }
        }
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (failFast) {
            return onlineState;
        } else if (onlineState == State.UNKNOWN || onlineState == State.UNCHECKED) {
            loadModelInfo();
        }
        return onlineState;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        try {
            openWebsocket();
            log.debug("Paylist url {}", playlistUrl);
            return parseMasterPlaylist(playlistUrl);
        } catch (InterruptedException e1) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e1);
        } finally {
            closeWebsocket();
        }
    }

    private List<StreamSource> parseMasterPlaylist(String playlistUrl) throws IOException, ParseException, PlaylistException {
        List<StreamSource> sources = new ArrayList<>();
        Request req = new Request.Builder()
                .url(playlistUrl)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ORIGIN, Fc2Live.BASE_URL)
                .header(REFERER, getUrl())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                InputStream inputStream = response.body().byteStream();
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8);
                Playlist playlist = parser.parse();
                MasterPlaylist master = playlist.getMasterPlaylist();
                for (PlaylistData playlistData : master.getPlaylists()) {
                    StreamSource streamsource = new StreamSource();
                    streamsource.setMediaPlaylistUrl(playlistData.getUri());
                    if (playlistData.hasStreamInfo()) {
                        StreamInfo info = playlistData.getStreamInfo();
                        streamsource.setBandwidth(info.getBandwidth());
                        streamsource.setWidth(info.hasResolution() ? info.getResolution().width : 0);
                        streamsource.setHeight(info.hasResolution() ? info.getResolution().height : 0);
                    } else {
                        streamsource.setBandwidth(0);
                        streamsource.setWidth(0);
                        streamsource.setHeight(0);
                    }
                    sources.add(streamsource);
                }
                log.debug(sources.toString());
                return sources;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void getControlToken(BiConsumer<String, String> callback) throws IOException {
        String url = Fc2Live.BASE_URL + "/api/getControlServer.php";
        RequestBody body = new FormBody.Builder()
                .add("channel_id", id)
                .add("channel_version", version)
                .add("client_app", "browser_hls")
                .add("client_type", "pc")
                .add("client_version", "1.6.0 [1]")
                .add("mode", "play")
                .build();
        Request req = new Request.Builder()
                .url(url)
                .method("POST", body)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, Fc2Live.BASE_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        log.debug("Fetching page {}", url);
        try (Response resp = getSite().getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String msg = resp.body().string();
                JSONObject json = new JSONObject(msg);
                if (json.has("url")) {
                    String wssurl = json.getString("url");
                    String token = json.getString("control_token");
                    callback.accept(token, wssurl);
                } else {
                    throw new IOException("Couldn't determine websocket url");
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    @Override
    public void invalidateCacheEntries() {
        // not needed
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        // tipping is not implemented for FC2
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        return new int[2];
    }

    @Override
    public boolean follow() throws IOException {
        return followUnfollow("add");
    }

    @Override
    public boolean unfollow() throws IOException {
        return followUnfollow("remove");
    }

    private boolean followUnfollow(String mode) throws IOException {
        if (!getSite().getHttpClient().login()) {
            throw new IOException("Login didn't work");
        }

        RequestBody body = new FormBody.Builder()
                .add("id", getId())
                .add("mode", mode)
                .build();
        Request req = new Request.Builder()
                .url(getSite().getBaseUrl() + "/api/favoriteManager.php")
                .header(REFERER, getUrl())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build();
        try (Response resp = getSite().getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String content = resp.body().string();
                JSONObject json = new JSONObject(content);
                return json.optInt("status") == 1;
            } else {
                log.error("Login failed {} {}", resp.code(), resp.message());
                return false;
            }
        }
    }

    /**
     * Opens a chat websocket connection. This connection is used to retrieve the HLS playlist url. It also has to be kept open as long as the HLS stream is
     * "played". Fc2Model keeps track of the number of objects, which tried to open or close the websocket. As long as at least one object is using the
     * websocket, it is kept open. If the last object, which is using it, calls closeWebsocket, the websocket is closed.
     */
    public void openWebsocket() throws InterruptedException, IOException {
        messageId = 1;
        int usage = websocketUsage.incrementAndGet();
        log.debug("{} objects using the websocket for {}", usage, this);
        if (ws == null) {
            Object monitor = new Object();
            loadModelInfo();
            getControlToken((token, url) -> {
                url = url + "?control_token=" + token;
                log.debug("Session token: {}", token);
                log.debug("Getting playlist token over websocket {}", url);

                Request request = new Request.Builder()
                        .url(url)
                        .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                        .header(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header(ACCEPT_LANGUAGE, "de,en-US;q=0.7,en;q=0.3")
                        .build();
                ws = getSite().getHttpClient().newWebSocket(request, new WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket webSocket, Response response) {
                        response.close();
                        webSocket.send("{\"name\":\"get_hls_information\",\"arguments\":{},\"id\":" + (messageId++) + "}");
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, String text) {
                        JSONObject json = new JSONObject(text);
                        if (json.optString("name").equals("_response_")) {
                            if (json.has("arguments")) {
                                JSONObject args = json.getJSONObject("arguments");
                                if (args.has("playlists_high_latency")) {
                                    JSONArray playlists = args.getJSONArray("playlists_high_latency");
                                    JSONObject playlist = playlists.getJSONObject(0);
                                    playlistUrl = playlist.getString("url");
                                    log.debug("Master Playlist: {}", playlistUrl);
                                    synchronized (monitor) {
                                        monitor.notifyAll();
                                    }
                                } else {
                                    log.trace(json.toString());
                                }
                            }
                        } else if (json.optString("name").equals("user_count") || json.optString("name").equals("comment")) {
                            // ignore
                            log.trace("WS <-- {}: {}", getName(), text);
                        } else {
                            log.trace("WS <-- {}: {}", getName(), text);
                        }

                        // send heartbeat every now and again
                        long now = System.currentTimeMillis();
                        if ((now - lastHeartBeat) > TimeUnit.SECONDS.toMillis(30)) {
                            webSocket.send("{\"name\":\"heartbeat\",\"arguments\":{},\"id\":" + messageId + "}");
                            lastHeartBeat = now;
                            log.trace("Sending heartbeat for {} (messageId: {})", getName(), messageId);
                            messageId++;
                        }
                    }

                    @Override
                    public void onMessage(WebSocket webSocket, ByteString bytes) {
                        log.debug("ws btxt {}", bytes);
                    }

                    @Override
                    public void onClosed(WebSocket webSocket, int code, String reason) {
                        log.debug("ws closed {} - {}", code, reason);
                    }

                    @Override
                    public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                        log.debug("ws failure", t);
                        response.close();
                    }
                });
            });
            synchronized (monitor) {
                // wait at max 10 seconds, otherwise we can assume, that the stream is not available
                monitor.wait(TimeUnit.SECONDS.toMillis(20));
            }
            if (playlistUrl == null) {
                throw new IOException("No playlist response for 20 seconds");
            }
        }
    }

    public void closeWebsocket() {
        int websocketUsers = websocketUsage.decrementAndGet();
        log.debug("{} objects using the websocket for {}", websocketUsers, this);
        if (websocketUsers == 0) {
            log.debug("Closing the websocket for {}", this);
            ws.close(1000, "");
            ws = null;
        }
    }

    @Override
    public RecordingProcess createDownload() {
        if (Config.getInstance().getSettings().useHlsdl) {
            return new Fc2HlsdlDownload();
        } else {
            if (Config.isServerMode() && !Config.getInstance().getSettings().recordSingleFile) {
                return new Fc2HlsDownload(getSite().getHttpClient());
            } else {
                return new Fc2MergedHlsDownload(getSite().getHttpClient());
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

    @Override
    public String getSanitizedNamed() {
        return getId();
    }
}
