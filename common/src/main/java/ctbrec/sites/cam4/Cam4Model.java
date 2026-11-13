package ctbrec.sites.cam4;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.StreamInfo;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.NotImplementedExcetion;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.HttpHeaderFactoryImpl;
import ctbrec.recorder.download.StreamSource;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpClient.bodyToJsonObject;
import static ctbrec.io.HttpConstants.*;

@Slf4j
public class Cam4Model extends AbstractModel {

    @Setter
    private String playlistUrl;
    private int[] resolution = null;
    private transient JSONObject modelInfo;
    private transient Instant lastInfoRequest = Instant.EPOCH;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            try {
                modelInfo = loadModelInfo();
                if (modelInfo.optBoolean("privateRoom")) {
                    onlineState = PRIVATE;
                }
            } catch (Exception e) {
                onlineState = OFFLINE;
            }
        }
        return onlineState == ONLINE;
    }

    private JSONObject loadModelInfo() throws IOException {
        if (Objects.nonNull(modelInfo) && Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            return modelInfo;
        }
        lastInfoRequest = Instant.now();
        modelInfo = new Cam4WsClient(Config.getInstance(), (Cam4) getSite(), this).getRoomState();
        log.trace(modelInfo.toString(2));
        String state = modelInfo.optString("newShowsState");
        setOnlineStateByShowType(state);
        setDescription(modelInfo.optString("status"));
        return modelInfo;
    }

    public void setOnlineStateByShowType(String showType) {
        switch (showType) {
            case "NORMAL", "ACCEPTING", "GROUP_SHOW_SELLING_TICKETS", "GS_SELLING_TICKETS", "GS_SELLING_TICKETS_UNSUCCESSFUL", "GS_GOAL_REACHED" ->
                    onlineState = ONLINE;
            case "PRIVATE_SHOW", "INSIDE_PS" -> onlineState = PRIVATE;
            case "INSIDE_GS", "GROUP_SHOW" -> onlineState = GROUP;
            case "PAUSED" -> onlineState = AWAY;
            case "OFFLINE" -> onlineState = OFFLINE;
            default -> {
                log.debug("############################## Unknown show type [{} {}]", this, showType);
                onlineState = UNKNOWN;
            }
        }

    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        // TODO: should it be !failFast || onlineState ? as in AbstractHlsModel
        if (!failFast && (onlineState == UNKNOWN || onlineState == UNCHECKED)) {
            try {
                modelInfo = loadModelInfo();
            } catch (Exception e) {
                log.warn("Couldn't load model details {}", e.getMessage());
            }
        }
        return onlineState;
    }

    private String getPlaylistUrl() throws IOException {
        if (Objects.nonNull(modelInfo)) {
            String hls = modelInfo.optString("hls");
            if (StringUtil.isNotBlank(hls) && hls.startsWith("http")) {
                log.debug("Stream hls: {}", hls);
                playlistUrl = hls;
                return playlistUrl;
            }
            String llhls = modelInfo.optString("llhls");
            if (StringUtil.isNotBlank(llhls) && llhls.startsWith("http")) {
                log.debug("Stream hls: {}", llhls);
                playlistUrl = llhls;
                return playlistUrl;
            }
        }
        try {
            getPlaylistUrlFromStreamUrl();
        } catch (IOException e) {
            log.debug("Couldn't get playlist url from stream info: {}", e.getMessage());
        }
        if (Objects.nonNull(modelInfo) && StringUtil.isBlank(playlistUrl)) {
            String uuid = modelInfo.optString("streamUUID");
            if (StringUtil.isNotBlank(uuid) && uuid.toLowerCase().startsWith(getName().toLowerCase())) {
                String[] parts = uuid.split("-");
                if (parts.length > 5) {
                    String urlTemplate = "https://cam4-hls.xcdnpro.com/{0}/cam4-origin-live/{1}_aac/playlist.m3u8";
                    playlistUrl = MessageFormat.format(urlTemplate, parts[1], uuid);
                    log.debug("Stream UUID: {}, hls: {}", uuid, playlistUrl);
                    return playlistUrl;
                }
            }
        }
        if (StringUtil.isBlank(playlistUrl)) {
            throw new IOException("Couldn't determine playlist url");
        }
        return playlistUrl;
    }

    private void getPlaylistUrlFromStreamUrl() throws IOException {
        String url = getSite().getBaseUrl() + "/rest/v1.0/profile/" + getName() + "/streamInfo";
        log.trace("Getting playlist url from {}", url);
        Request req = new Request.Builder() // @formatter:off
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "*")
                .header(CACHE_CONTROL, NO_CACHE)
                .header(PRAGMA, NO_CACHE)
                .header(REFERER, getUrl())
                .build(); // @formatter:on
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(bodyToJsonObject(response));
                if (log.isTraceEnabled()) log.trace(json.toString(2));
                if (json.has("canUseCDN")) {
                    if (json.getBoolean("canUseCDN")) {
                        playlistUrl = json.optString("cdnURL");
                    } else {
                        playlistUrl = json.optString("edgeURL");
                    }
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        MasterPlaylist masterPlaylist = getMasterPlaylist();
        List<StreamSource> sources = new ArrayList<>();
        for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
            if (playlist.hasStreamInfo()) {
                StreamSource src = new StreamSource();
                src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                src.setHeight(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getResolution).map(res -> res.height).orElse(0));
                src.setWidth(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getResolution).map(res -> res.width).orElse(0));
                if (playlist.getUri().startsWith("http")) {
                    src.setMediaPlaylistUrl(playlist.getUri());
                } else {
                    String baseUrl = playlistUrl.substring(0, playlistUrl.lastIndexOf('/') + 1);
                    src.setMediaPlaylistUrl(baseUrl + playlist.getUri());
                }
                log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    private MasterPlaylist getMasterPlaylist() throws IOException, ParseException, PlaylistException {
        String masterPlaylistUrl = getPlaylistUrl();
        masterPlaylistUrl = masterPlaylistUrl.replace("_sfm4s", "");
        log.debug("Loading master playlist [{}]", masterPlaylistUrl);
        Request.Builder builder = new Request.Builder().url(masterPlaylistUrl);
        getHttpHeaderFactory().createMasterPlaylistHeaders().forEach(builder::header);
        Request req = builder.build();

        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                InputStream inputStream = response.body().byteStream();
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                return playlist.getMasterPlaylist();
            } else {
                throw new HttpException(response.code(), "Couldn't download HLS playlist " + masterPlaylistUrl);
            }
        }
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = null;
        playlistUrl = null;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        throw new NotImplementedExcetion("Tipping is not implemented for Cam4, yet");
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
        String url = site.getBaseUrl() + "/profiles/addFriendFavorite?action=addFavorite&object=" + getName() + "&_=" + System.currentTimeMillis();
        Request req = new Request.Builder()
                .url(url)
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            return response.isSuccessful();
        }
    }

    @Override
    public boolean unfollow() throws IOException {
        // send unfollow request
        String username = Config.getInstance().getSettings().cam4Username;
        String url = site.getBaseUrl() + "/rest/v1.0/favorites/" + username + '/' + getName();
        Request req = new Request.Builder()
                .url(url)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .delete()
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            return resp.isSuccessful();
        }
    }

    @Override
    public void setUrl(String url) {
        String normalizedUrl = url.toLowerCase();
        if (normalizedUrl.endsWith("/")) {
            normalizedUrl = normalizedUrl.substring(0, normalizedUrl.length() - 1);
        }
        super.setUrl(normalizedUrl);
    }

    @Override
    public HttpHeaderFactory getHttpHeaderFactory() {
        HttpHeaderFactoryImpl fac = new HttpHeaderFactoryImpl();
        Map<String, String> headers = new HashMap<>();
        headers.put(ACCEPT, "*/*");
        headers.put(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage());
        headers.put(CONNECTION, KEEP_ALIVE);
        if (getSite() != null) {
            headers.put(ORIGIN, getSite().getBaseUrl());
            headers.put(REFERER, getSite().getBaseUrl());
        }
        headers.put(USER_AGENT, Config.getInstance().getSettings().httpUserAgent);
        fac.setMasterPlaylistHeaders(headers);
        fac.setSegmentPlaylistHeaders(headers);
        fac.setSegmentHeaders(headers);
        return fac;
    }
}
