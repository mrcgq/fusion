package ctbrec.sites.stripchat;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.HlsdlDownload;
import ctbrec.sites.stripchat.mouflon.Mouflon;
import ctbrec.sites.stripchat.mouflon.MouflonSettings;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;
import static ctbrec.sites.stripchat.StripchatHttpClient.JSON;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class StripchatModel extends AbstractModel {
    private static final String KEY_MODEL_TOKEN = "modelToken";
    private int[] resolution = new int[]{0, 0};
    private transient JSONObject modelInfo;
    private transient Instant lastInfoRequest = Instant.EPOCH;
    /** 上一次抓 master 得到的 MOUFLON PSCH seed，注入到变体媒体播放列表当 pkey。 */
    private transient String masterPkey;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            JSONObject jsonResponse = getModelInfo();
            if (jsonResponse.has("user")) {
                JSONObject user = jsonResponse.getJSONObject("user").getJSONObject("user");
                String status = user.optString("status");
                mapOnlineState(status);
                if (onlineState == OFFLINE) {
                    setLastSeen(user.optString("statusChangedAt"));
                }
                if (isBanned(user)) {
                    log.debug("Model inactive or deleted: {}", getName());
                    setMarkedForLaterRecording(true);
                }
                // 私人房带 modelToken 视作可录（spy）
                if ((onlineState == PRIVATE) && jsonResponse.has("cam")) {
                    JSONObject cam = jsonResponse.getJSONObject("cam");
                    if (StringUtil.isNotBlank(cam.optString(KEY_MODEL_TOKEN))) {
                        setOnlineState(ONLINE);
                        return true;
                    }
                }
            }
            if (jsonResponse.optString("error").equals("Not Found")) {
                setMarkedForLaterRecording(true);
                setOnlineState(OFFLINE);
            }
        }
        return onlineState == ONLINE;
    }

    private boolean isBanned(JSONObject user) {
        boolean isDeleted = user.optBoolean("isDeleted", false);
        boolean isApprovedModel = user.optBoolean("isApprovedModel", true);
        return (!isApprovedModel && isDeleted);
    }

    private void setLastSeen(String date) {
        try {
            if (StringUtil.isNotBlank(date)) {
                setLastSeen(Instant.parse(date));
            }
        } catch (Exception e) {
            // fail silently
        }
    }

    private void mapOnlineState(String status) {
        switch (status) {
            case "public" -> setOnlineState(ONLINE);
            case "idle" -> setOnlineState(AWAY);
            case "private", "p2p", "groupShow", "virtualPrivate" -> setOnlineState(PRIVATE);
            case "off" -> setOnlineState(OFFLINE);
            default -> {
                log.debug("Unknown online state {} for model {}", status, getName());
                setOnlineState(OFFLINE);
            }
        }
    }

    private JSONObject getModelInfo() throws IOException {
        if (Objects.nonNull(modelInfo) && Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            return modelInfo;
        }
        lastInfoRequest = Instant.now();
        // 走 IdResolver（suggestion + /models/{id}），绕开现网 418 的 /cam
        modelInfo = StripchatIdResolver.lookup((Stripchat) getSite(), getName());
        return modelInfo;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        String url = getMasterPlaylistUrl();
        MasterPlaylist masterPlaylist = getMasterPlaylist(url);
        List<StreamSource> streamSources = extractStreamSources(masterPlaylist);
        if (streamSources.isEmpty()) {
            // 部分 edge 只回单码率：把 master 自己当媒体播放列表
            StreamSource single = new StreamSource();
            single.setBandwidth(0);
            single.setHeight(0);
            single.setWidth(0);
            single.setMediaPlaylistUrl(StripchatEdgeResolver.keepQueryString(url));
            streamSources.add(single);
        }
        // The plain master only exposes the single rung the room pushes (which is the origin
        // stream, i.e. the best one). The "_auto" master exposes the whole ladder, listed in
        // *descending* order with the origin stream first. Merge it in so that the UI can offer
        // the lower rungs and the resolution filters still have something to match against,
        // without ever letting a lower rung outrank the plain master's origin stream.
        try {
            String autoUrl = url.contains("_auto") || url.contains("_vr")
                    ? url
                    : StripchatEdgeResolver.normalizeMasterUrl((Stripchat) getSite(),
                            url.replace(".m3u8", "_auto.m3u8"));
            if (autoUrl != null && !autoUrl.equals(url)) {
                for (StreamSource auto : extractStreamSources(getMasterPlaylist(autoUrl))) {
                    boolean found = streamSources.stream()
                            .anyMatch(s -> s.getHeight() == auto.getHeight() && s.getWidth() == auto.getWidth());
                    if (!found) {
                        streamSources.add(auto);
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Auto quality master not available for {}", getName());
        }
        return streamSources;
    }

    List<StreamSource> extractStreamSources(MasterPlaylist masterPlaylist) {
        List<StreamSource> sources = new ArrayList<>();
        for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
            if (playlist.hasStreamInfo()) {
                StreamSource src = new StreamSource();
                src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                src.setHeight(playlist.getStreamInfo().getResolution().height);
                src.setWidth(playlist.getStreamInfo().getResolution().width);
                // 关键：保留变体 URI 上的查询串（psch/pkey/codec），否则段会落到 CPA 假列表或 404
                String uri = StripchatEdgeResolver.keepQueryString(playlist.getUri());
                // 变体不带鉴权串时补 psch=v2&pkey=<master seed>（否则拿到的是 CPA 广告 VOD 假列表）
                if (uri != null && !uri.toLowerCase().contains("psch=")) {
                    String pk = masterPkey == null
                            ? ctbrec.recorder.download.hls.StripchatPlaylist.pkey(Config.getInstance())
                            : masterPkey;
                    uri = uri + (uri.contains("?") ? "&" : "?") + "psch=v2" + (pk.isEmpty() ? "" : "&pkey=" + pk);
                }
                src.setMediaPlaylistUrl(uri);
                log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    private MasterPlaylist getMasterPlaylist(String url) throws IOException, ParseException, PlaylistException {
        log.trace("Loading master playlist {}", url);
        String fixed = StripchatEdgeResolver.normalizeMasterUrl((Stripchat) getSite(), url);
        Request req = new Request.Builder()
                .url(fixed)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "application/vnd.apple.mpegurl")
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                log.trace("master body for {}:\n{}", getName(), body);
                java.util.List<String> seeds = ctbrec.sites.stripchat.mouflon.Mouflon.pschSeeds(body);
                if (!seeds.isEmpty()) {
                    ctbrec.sites.stripchat.mouflon.Mouflon.rememberMasterSeeds(getName(), body);
                    // The master lists ~11 PSCH values but only one of them is a real keyId; the
                    // rest are decoys. A playlist fetched with a decoy still parses as live yet no
                    // segment name decodes, so prefer the configured keyId (which pairs with the
                    // configured xor key) over the first line.
                    String configured = ctbrec.sites.stripchat.mouflon.MouflonSettings.getInstance().getPkey();
                    if (configured == null || configured.isBlank()) {
                        configured = Config.getInstance().getSettings().pkeyV2;
                    }
                    if (configured != null && !configured.isBlank()) {
                        masterPkey = configured.trim();
                        if (!seeds.contains(masterPkey)) {
                            log.debug("Configured pkey {} not among the {} seeds of {} ({} known pairings still apply)",
                                    masterPkey, seeds.size(), getName(), seeds);
                        }
                    } else {
                        masterPkey = seeds.get(0);
                    }
                    log.debug("MOUFLON seeds for {}: {} (pkey={})", getName(), seeds.size(), masterPkey);
                }
                InputStream inputStream = new ByteArrayInputStream(body.getBytes(UTF_8));
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                return playlist.getMasterPlaylist();
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    /**
     * master URL：走 EdgeResolver 的 edge-hls.doppiocdn.net/hls/{id}/master/{id}.m3u8。
     * 私人房带 modelToken 时追加 aclAuth。VR 由设置决定。
     */
    private String getMasterPlaylistUrl() throws IOException {
        JSONObject info = getModelInfo();
        if (info.has("user")) {
            JSONObject user = info.getJSONObject("user").getJSONObject("user");
            long id = user.optLong("id");
            if (id <= 0) throw new IOException("Model id not resolved for " + getName());

            boolean saveVR = Config.getInstance().getSettings().stripchatVR;
            boolean isVRStream = user.optBoolean("isVr", false);
            String suffix = (saveVR && isVRStream) ? "_vr" : "";

            String token = "";
            if (info.has("cam")) {
                JSONObject cam = info.getJSONObject("cam");
                if (StringUtil.isNotBlank(cam.optString(KEY_MODEL_TOKEN))) {
                    token = "?aclAuth=" + cam.getString(KEY_MODEL_TOKEN);
                    log.debug("Spy start for {}", getName());
                }
            }
            return StripchatEdgeResolver.masterUrl((Stripchat) getSite(), id, suffix) + token;
        }
        throw new IOException("Playlist URL not found for " + getName());
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = new int[]{0, 0};
        lastInfoRequest = Instant.EPOCH;
        modelInfo = null;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        // not implemented
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (!failFast) {
            try {
                List<StreamSource> sources = getStreamSources();
                if (!sources.isEmpty()) {
                    // Stripchat's "_auto" master lists the rungs in *descending* order (the origin
                    // stream first), so the last element is the lowest quality. Sort with the same
                    // comparator the recorder uses before taking the top rung.
                    Collections.sort(sources);
                    StreamSource best = sources.get(sources.size() - 1);
                    resolution = new int[]{best.getWidth(), best.getHeight()};
                }
            } catch (IOException | ParseException | PlaylistException e) {
                throw new ExecutionException(e);
            }
        }
        return resolution;
    }

    @Override
    public boolean follow() throws IOException {
        StripchatHttpClient client = (StripchatHttpClient) getSite().getHttpClient();
        client.login();
        long id = resolveId();
        String url = Stripchat.getBaseUri() + "/api/front/users/" + client.getUserId() + "/favorites/" + id;
        JSONObject requestParams = new JSONObject()
                .put("csrfToken", client.getCsrfToken())
                .put("csrfTimestamp", client.getCsrfTimestamp())
                .put("csrfNotifyTimestamp", client.getCsrfNotifyTimestamp())
                .put("uniq", getUniq())
                .put("ampl", client.getAmpl());
        RequestBody body = RequestBody.Companion.create(requestParams.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ORIGIN, Stripchat.getBaseUri())
                .header(REFERER, Stripchat.getBaseUri() + '/' + getName())
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .put(body)
                .build();
        try (Response response = client.execute(request)) {
            if (response.isSuccessful()) {
                return true;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean unfollow() throws IOException {
        StripchatHttpClient client = (StripchatHttpClient) getSite().getHttpClient();
        client.login();
        long id = resolveId();
        JSONArray favoriteIds = new JSONArray().put(id).put(id);
        String url = Stripchat.getBaseUri() + "/api/front/users/" + client.getUserId() + "/favorites";
        JSONObject requestParams = new JSONObject()
                .put("favoriteIds", favoriteIds)
                .put("csrfToken", client.getCsrfToken())
                .put("csrfTimestamp", client.getCsrfTimestamp())
                .put("csrfNotifyTimestamp", client.getCsrfNotifyTimestamp())
                .put("uniq", getUniq());
        RequestBody body = RequestBody.Companion.create(requestParams.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ORIGIN, Stripchat.getBaseUri())
                .header(REFERER, Stripchat.getBaseUri())
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .delete(body)
                .build();
        try (Response response = client.execute(request)) {
            if (response.isSuccessful()) {
                return true;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private long resolveId() throws IOException {
        JSONObject info = getModelInfo();
        if (info.has("user")) {
            long id = info.getJSONObject("user").getJSONObject("user").optLong("id");
            if (id > 0) return id;
        }
        throw new IOException("Model id not resolved for " + getName());
    }

    @Override
    public boolean exists() throws IOException {
        JSONObject jsonResponse = getModelInfo();
        if (jsonResponse.optString("error").equals("Not Found")) {
            log.info("Model not found: {}", getName());
            return false;
        }
        if (jsonResponse.has("user")) {
            JSONObject user = jsonResponse.getJSONObject("user").getJSONObject("user");
            if (isBanned(user)) {
                log.info("Model inactive or deleted: {}", getName());
                return false;
            }
        }
        return true;
    }

    @Override
    public RecordingProcess createDownload() {
        if (Config.getInstance().getSettings().useHlsdl) {
            return new HlsdlDownload();
        }
        return new StripchatFfmpegHlsDownload(getSite().getHttpClient());
    }

    private String getUniq() {
        return ((Stripchat) getSite()).getUniq();
    }
}
