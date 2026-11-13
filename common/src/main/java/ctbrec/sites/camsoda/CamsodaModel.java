package ctbrec.sites.camsoda;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.StreamInfo;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;

@Slf4j
public class CamsodaModel extends AbstractModel {

    private static final String STREAM_NAME = "stream_name";
    private static final String EDGE_SERVERS = "edge_servers";
    private static final String STATUS = "status";
    private transient List<StreamSource> streamSources = null;
    private transient boolean isNew;
    @Getter
    @Setter
    private transient String gender;
    @Getter
    @Setter
    private transient boolean isVoyeur;
    @Getter
    @Setter
    private float sortOrder = 0;
    private final Random random = new Random();
    int[] resolution = new int[2];


    public String getStreamUrl() throws IOException {
        Request req = createJsonRequest(getTokenInfoUrl());
        JSONObject response = executeJsonRequest(req);
        if (response.optInt(STATUS) == 1 && response.optJSONArray(EDGE_SERVERS) != null && !response.optJSONArray(EDGE_SERVERS).isEmpty()) {
            String edgeServer = response.getJSONArray(EDGE_SERVERS).getString(0);
            String streamName = response.getString(STREAM_NAME);
            String token = response.optString("token");
            return constructStreamUrl(edgeServer, streamName, token);
        } else {
            throw new JSONException("JSON response has not the expected structure");
        }
    }

    private String getTokenInfoUrl() {
        String guestUsername = "guest_" + 10_000 + random.nextInt(50_000);
        String tokenInfoUrl = site.getBaseUrl() + "/api/v1/video/vtoken/" + getName() + "?username=" + guestUsername;
        return tokenInfoUrl;
    }

    private String constructStreamUrl(String edgeServer, String streamName, String token) {
        StringBuilder url = new StringBuilder("https://");
        url.append(edgeServer).append('/');
        url.append(streamName);
        url.append("_v1/index.ll.m3u8?multitrack=true&filter=tracks:v4v3v2v1a1a2");
        if (!isPublic(streamName)) {
            url.append("&token=").append(token);
        }
        log.trace("Stream URL: {}", url);
        return url.toString();
    }

    private Request createJsonRequest(String tokenInfoUrl) {
        return new Request.Builder()
                .url(tokenInfoUrl)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
    }

    private JSONObject executeJsonRequest(Request request) throws IOException {
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                return jsonResponse;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private boolean isPublic(String streamName) {
        return Optional.ofNullable(streamName).orElse("").contains("_public");
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        try {
            String playlistUrl = getStreamUrl();
            if (playlistUrl == null) {
                return Collections.emptyList();
            }
            log.trace("Loading playlist {}", playlistUrl);
            Request req = new Request.Builder()
                    .url(playlistUrl)
                    .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .build();
            try (Response response = site.getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    InputStream inputStream = response.body().byteStream();
                    PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                    Playlist playlist = parser.parse();
                    MasterPlaylist master = playlist.getMasterPlaylist();
                    streamSources = new ArrayList<>();
                    for (PlaylistData playlistData : master.getPlaylists()) {
                        StreamSource streamsource = new StreamSource();
                        int cutOffAt = Math.max(playlistUrl.indexOf("index.ll.m3u8"), playlistUrl.indexOf("playlist.m3u8"));
                        String segmentPlaylistUrl = playlistData.getUri().startsWith("http") ? playlistData.getUri() : playlistUrl.substring(0, cutOffAt) + playlistData.getUri();
                        streamsource.setMediaPlaylistUrl(segmentPlaylistUrl);
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
                        streamSources.add(streamsource);
                    }
                } else {
                    log.trace("Response: {}", response.body().string());
                    throw new HttpException(playlistUrl, response.code(), response.message());
                }
            }
            return streamSources;
        } catch (JSONException e) {
            return Collections.emptyList();
        }
    }

    private void loadModel() throws IOException {
        String modelUrl = site.getBaseUrl() + "/api/v1/chat/react/" + getName();
        Request req = new Request.Builder()
                .url(modelUrl)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                try {
                    JSONObject result = new JSONObject(body);
                    JSONObject chat = result.getJSONObject("chat");
                    String status = chat.getString(STATUS);
                    setOnlineStateByStatus(status);
                    if (onlineState == OFFLINE) {
                        setLastSeen(chat.optString("lastOnlineAt"));
                    }
                } catch (JSONException e) {
                    throw new IOException("Couldn't parse body as JSON:\n" + body, e);
                }
            } else throw new HttpException(response.code(), response.message());
        }
    }

    public void setOnlineStateByStatus(String status) {
        switch (status) {
            case "online" -> onlineState = ONLINE;
            case "offline" -> onlineState = OFFLINE;
            case "connected" -> onlineState = AWAY;
            case "private" -> onlineState = PRIVATE;
            case "limited" -> onlineState = GROUP;
            default -> {
                log.debug("Unknown show type {}", status);
                onlineState = UNKNOWN;
            }
        }
    }

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            loadModel();
        }
        return onlineState == ONLINE;
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (!failFast && (onlineState == UNKNOWN || onlineState == UNCHECKED)) {
            loadModel();
        }
        return onlineState;
    }

    private void setLastSeen(String date) {
        try {
            if (StringUtil.isNotBlank(date)) {
                setLastSeen(Instant.parse(date.replace("+0000", ".00Z")));
            }
        } catch (Exception e) {
            // fail silently
        }
    }

    @Override
    public void invalidateCacheEntries() {
        streamSources = null;
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (failFast) {
            return resolution;
        } else {
            try {
                List<StreamSource> sources = getStreamSources();
                log.debug("{}:{} stream sources {}", getSite().getName(), getName(), sources);
                if (sources.isEmpty()) {
                    return new int[]{0, 0};
                } else {
                    StreamSource src = sources.getLast();
                    resolution = new int[]{src.getWidth(), src.getHeight()};
                    return resolution;
                }
            } catch (IOException | ParseException | PlaylistException e) {
                throw new ExecutionException(e);
            }
        }
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        String csrfToken = ((CamsodaHttpClient) site.getHttpClient()).getCsrfToken();
        String url = site.getBaseUrl() + "/api/v1/tip/" + getName();
        if (!Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
            log.debug("Sending tip {}", url);
            RequestBody body = new FormBody.Builder()
                    .add("amount", Integer.toString(tokens.intValue()))
                    .add("comment", "")
                    .build();
            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .addHeader(REFERER, Camsoda.BASE_URI + '/' + getName())
                    .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                    .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .addHeader(X_CSRF_TOKEN, csrfToken)
                    .build();
            try (Response response = site.getHttpClient().execute(request)) {
                if (!response.isSuccessful()) {
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
    }

    @Override
    public boolean follow() throws IOException {
        String url = Camsoda.BASE_URI + "/api/v1/follow/" + getName();
        log.debug("Sending follow request {}", url);
        String csrfToken = ((CamsodaHttpClient) site.getHttpClient()).getCsrfToken();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0]))
                .addHeader(REFERER, Camsoda.BASE_URI + '/' + getName())
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(X_CSRF_TOKEN, csrfToken)
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                return true;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean unfollow() throws IOException {
        String url = Camsoda.BASE_URI + "/api/v1/unfollow/" + getName();
        log.debug("Sending unfollow request {}", url);
        String csrfToken = ((CamsodaHttpClient) site.getHttpClient()).getCsrfToken();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0]))
                .addHeader(REFERER, Camsoda.BASE_URI + '/' + getName())
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(X_CSRF_TOKEN, csrfToken)
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                return true;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public boolean isNew() {
        return isNew;
    }

    public void setNew(boolean isNew) {
        this.isNew = isNew;
    }
}
