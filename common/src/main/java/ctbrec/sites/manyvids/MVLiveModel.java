package ctbrec.sites.manyvids;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.StreamInfo;
import ctbrec.*;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.ModelOfflineException;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class MVLiveModel extends AbstractModel {

    private transient MVLiveHttpClient httpClient;
    private transient MVLiveClient client;
    private transient JSONObject roomLocation;
    private transient Instant lastRoomLocationUpdate = Instant.EPOCH;
    private String roomNumber;
    @Getter
    @Setter
    private String id;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            String urlHandle = getDisplayName().toLowerCase().replace(" ", "-");
            String url = "https://api.vidchat.manyvids.com/creator?urlHandle=" + URLEncoder.encode(urlHandle, UTF_8);
            Request req = new Request.Builder()
                    .url(url)
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .build();
            try (Response response = getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    JSONObject creator = new JSONObject(body);
                    updateStateFromJson(creator);
                } else {
                    log.debug("{} URL: {}\n\tResponse: {}", response.code(), url, response.body().string());
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
        return this.onlineState == ONLINE;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        log.debug("Loading {}", getUrl());
        try {
            StreamLocation streamLocation = getClient().getStreamLocation(this);
            log.debug("Got the stream location from WS {}", streamLocation.masterPlaylist);
            roomNumber = streamLocation.roomNumber;
            updateCloudFlareCookies();
            MasterPlaylist masterPlaylist = getMasterPlaylist(streamLocation.masterPlaylist);
            List<StreamSource> sources = new ArrayList<>();
            for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
                if (playlist.hasStreamInfo()) {
                    StreamSource src = new StreamSource();
                    src.setBandwidth(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getBandwidth).orElse(0));
                    src.setHeight(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getResolution).map(res -> res.height).orElse(0));
                    src.setWidth(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getResolution).map(res -> res.width).orElse(0));
                    String masterUrl = streamLocation.masterPlaylist;
                    String baseUrl = masterUrl.substring(0, masterUrl.lastIndexOf('/') + 1);
                    String segmentUri = baseUrl + playlist.getUri();
                    src.setMediaPlaylistUrl(segmentUri);
                    if (src.getMediaPlaylistUrl().contains("?")) {
                        src.setMediaPlaylistUrl((src.getMediaPlaylistUrl().substring(0, src.getMediaPlaylistUrl().lastIndexOf('?'))));
                    }
                    log.debug("Media playlist {}", src.getMediaPlaylistUrl());
                    sources.add(src);
                }
            }
            return sources;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return Collections.emptyList();
    }

    private MasterPlaylist getMasterPlaylist(String url) throws IOException, ParseException, PlaylistException {
        log.trace("Loading master playlist {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                InputStream inputStream = new ByteArrayInputStream(body.getBytes(UTF_8));
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                MasterPlaylist master = playlist.getMasterPlaylist();
                return master;
            } else {
                log.debug("{} URL: {}\n\tResponse: {}", response.code(), url, response.body().string());
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public void updateCloudFlareCookies() throws IOException {
        String url = getApiUrl() + '/' + getRoomNumber() + "/player-settings/" + getDisplayName();
        log.trace("Getting CF cookies: {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (!response.isSuccessful()) {
                log.debug("Loading CF cookies not successful: {}", response.body().string());
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    String getApiUrl() throws JSONException, IOException {
        return getRoomLocation().getString("publicAPIURL");
    }

    public String getRoomNumber() throws IOException {
        if (StringUtil.isBlank(roomNumber)) {
            JSONObject json = getRoomLocation();
            if (json.optBoolean("success")) {
                roomNumber = json.getString("floorId");
            } else {
                log.debug("Room number response: {}", json.toString(2));
                throw new ModelOfflineException(this);
            }
        }
        return roomNumber;
    }

    private void fetchGeneralCookies() throws IOException {
        Request req = new Request.Builder()
                .url(getSite().getBaseUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getHttpClient().execute(req)) {
            if (!response.isSuccessful()) {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public JSONObject getRoomLocation() throws IOException {
        if (Duration.between(lastRoomLocationUpdate, Instant.now()).getSeconds() > 60) {
            fetchGeneralCookies();
            httpClient.fetchAuthenticationCookies();
            String url = "https://roompool.live.manyvids.com/roompool/" + getDisplayName() + "?private=false";
            log.debug("Fetching room location from {}", url);
            Request req = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                    .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(REFERER, MVLive.WS_ORIGIN + "/stream/" + getName())
                    .build();
            try (Response response = getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    roomLocation = new JSONObject(body);
                    log.debug("Room location response: {}", roomLocation);
                    lastRoomLocationUpdate = Instant.now();
                    return roomLocation;
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        } else {
            return roomLocation;
        }
    }

    private synchronized MVLiveClient getClient() {
        if (client == null) {
            client = new MVLiveClient(getHttpClient());
        }
        return client;
    }

    @Override
    public void invalidateCacheEntries() {
        roomNumber = null;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        throw new NotImplementedExcetion("Sending tips is not implemeted for MVLive");
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        return new int[]{1280, 720};
    }

    @Override
    public boolean follow() throws IOException {
        return false;
    }

    @Override
    public boolean unfollow() throws IOException {
        return false;
    }

    @Override
    public RecordingProcess createDownload() {
        if (Config.isServerMode() && !Config.getInstance().getSettings().recordSingleFile) {
            return new MVLiveHlsDownload(getHttpClient());
        } else {
            return new MVLiveMergedHlsDownload(getHttpClient());
        }
    }

    private synchronized MVLiveHttpClient getHttpClient() {
        if (httpClient == null) {
            MVLiveHttpClient siteHttpClient = (MVLiveHttpClient) getSite().getHttpClient();
            httpClient = siteHttpClient.newSession();
        }
        return httpClient;
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        data.put("id", id);
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        id = data.get("id");
    }

    public void updateStateFromJson(JSONObject creator) {
        setId(creator.getString("id"));
        setDisplayName(creator.optString("display_name", null));
        setUrl(creator.getString("session_url"));
        setOnlineState(mapState(creator.optString("live_status"), creator.optString("session_type")));
        setPreview(creator.optString("avatar", null));
    }

    protected Model.State mapState(String liveStatus, String sessionType) {
        if (Objects.equals("ONLINE", liveStatus)) {
            switch (sessionType) {
                case "PUBLIC" -> {
                    return ONLINE;
                }
                case "PRIVATE" -> {
                    return PRIVATE;
                }
                case "OFFLINE" -> {
                    return OFFLINE;
                }
                default -> {
                    log.debug("Unknown state {}", sessionType);
                    return OFFLINE;
                }
            }
        } else {
            return OFFLINE;
        }
    }
}
