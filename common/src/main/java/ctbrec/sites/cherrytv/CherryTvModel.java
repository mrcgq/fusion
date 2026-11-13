package ctbrec.sites.cherrytv;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import com.iheartradio.m3u8.data.StreamInfo;
import ctbrec.*;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class CherryTvModel extends AbstractModel {

    private static final Pattern NEXT_DATA = Pattern.compile("<script id=\"__NEXT_DATA__\" type=\"application/json\">(.*?)</script>");

    @Setter
    private boolean online = false;
    private int[] resolution;
    private String masterPlaylistUrl;
    @Getter
    @Setter
    private String id;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            String url = getUrl();
            Request req = new Request.Builder().url(url)
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(ACCEPT, "*")
                    .header(ACCEPT_LANGUAGE, "en")
                    .header(REFERER, getSite().getBaseUrl())
                    .build();
            try (Response resp = site.getHttpClient().execute(req)) {
                String body = Objects.requireNonNull(resp.body()).string();
                Matcher m = NEXT_DATA.matcher(body);
                if (m.find()) {
                    JSONObject json = new JSONObject(m.group(1));
                    updateModelProperties(json);
                } else {
                    log.error("NEXT_DATA not found in model page {}", getUrl());
                    return false;
                }
            } catch (JSONException e) {
                log.error("Unable to determine online state for {}. Probably the JSON structure in NEXT_DATA changed", getName());
            }
        }
        return online;
    }

    private void updateModelProperties(JSONObject json) {
        log.trace(json.toString(2));
        JSONObject apolloState = json.getJSONObject("props").getJSONObject("pageProps").getJSONObject("apolloState");
        online = false;
        onlineState = OFFLINE;
        for (Iterator<String> iter = apolloState.keys(); iter.hasNext(); ) {
            String key = iter.next();
            if (key.startsWith("Broadcast:")) {
                log.trace("Model properties:\n{}", apolloState.toString(2));
                JSONObject broadcast = apolloState.getJSONObject(key);
                setDisplayName(broadcast.optString("title"));
                online = broadcast.optString("showStatus").equalsIgnoreCase("Public")
                        && broadcast.optString("broadcastStatus").equalsIgnoreCase("Live");
                onlineState = online ? ONLINE : OFFLINE;
                masterPlaylistUrl = broadcast.optString("pullUrl", null);
            } else if (key.startsWith("Streamer:")) {
                JSONObject streamer = apolloState.getJSONObject(key);
                id = streamer.getString("id");
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
        try {
            isOnline(true);
            MasterPlaylist masterPlaylist = getMasterPlaylist();
            List<StreamSource> sources = new ArrayList<>();
            for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
                if (playlist.hasStreamInfo()) {
                    StreamSource src = new StreamSource();
                    src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                    src.setHeight(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getResolution).map(res -> res.height).orElse(0));
                    src.setWidth(Optional.ofNullable(playlist.getStreamInfo()).map(StreamInfo::getResolution).map(res -> res.width).orElse(0));
                    String masterUrl = masterPlaylistUrl;
                    String baseUrl = masterUrl.substring(0, masterUrl.lastIndexOf('/') + 1);
                    String segmentUri = baseUrl + playlist.getUri();
                    src.setMediaPlaylistUrl(segmentUri);
                    if (src.getMediaPlaylistUrl().contains("?")) {
                        src.setMediaPlaylistUrl(src.getMediaPlaylistUrl().substring(0, src.getMediaPlaylistUrl().lastIndexOf('?')));
                    }
                    log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                    sources.add(src);
                }
            }
            return sources;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ExecutionException(e);
        }
    }

    private MasterPlaylist getMasterPlaylist() throws IOException, ParseException, PlaylistException {
        if (masterPlaylistUrl == null) {
            log.info("Master playlist not found for {}:{}. This probably is webrtc stream", getSite().getName(), getName());
            throw new StreamNotFoundException("Webrtc streams are not supported for " + getSite().getName());
        }
        log.trace("Loading master playlist {}", masterPlaylistUrl);
        Request req = new Request.Builder()
                .url(masterPlaylistUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body()).string();
                log.trace(body);
                InputStream inputStream = new ByteArrayInputStream(body.getBytes(UTF_8));
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                MasterPlaylist master = playlist.getMasterPlaylist();
                return master;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = null;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        throw new NotImplementedExcetion();
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
        return followUnfollow("follow", "a7a8241014074f5c02ac83863a47f7579e5f03167a7ec424ff65ad045c7fcf6f");
    }

    @Override
    public boolean unfollow() throws IOException {
        return followUnfollow("unfollow", "e91f8f5a60d33efb2dfb3348b977b78358862d3a5cd5ef0011a6aa6bb65d0bd4");
    }

    private boolean followUnfollow(String action, String persistedQueryHash) throws IOException {
        Request request = createFollowUnfollowRequest(action, persistedQueryHash);
        log.debug("Sending follow request for model {} with ID {}", getName(), getId());
        try (Response response = getSite().getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                String responseBody = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                log.debug(responseBody);
                JSONObject resp = new JSONObject(responseBody);
                if (resp.has("data") && !resp.isNull("data")) {
                    JSONObject data = resp.getJSONObject("data");
                    if (data.has(action + "User")) {
                        return data.getJSONObject(action + "User").optBoolean("success");
                    }
                } else if (resp.has("errors")) {
                    JSONObject first = resp.getJSONArray("errors").getJSONObject(0);
                    if (first.optString("message").matches("You have .*? the user")) {
                        return true;
                    }
                }
                log.debug(resp.toString(2));
                return false;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private Request createFollowUnfollowRequest(String action, String persistedQueryHash) throws IOException {
        if (StringUtil.isBlank(id)) {
            try {
                // if the id is not set yet, we call isOnline(true), where it gets set
                isOnline(true);
            } catch (ExecutionException e) {
                throw new IOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
        }

        JSONObject body = new JSONObject()
                .put("operationName", action)
                .put("variables", new JSONObject()
                        .put("userId", Objects.requireNonNull(id, "Model ID is null"))
                )
                .put("query", "mutation " + action + "($userId: ID!) {\n  " + action + "User(userId: $userId) {\n    success\n    __typename\n  }\n}\n")
                .put("extensions", new JSONObject()
                        .put("persistedQuery", new JSONObject()
                                .put("version", 1)
                                .put("sha256Hash", persistedQueryHash)
                        )
                );

        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        return new Request.Builder()
                .url(CherryTv.BASE_URL + "/graphql")
                .header(REFERER, CherryTv.BASE_URL)
                .header(ORIGIN, CherryTv.BASE_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .post(requestBody)
                .build();
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        id = data.get("id");
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        data.put("id", id);
    }
}
