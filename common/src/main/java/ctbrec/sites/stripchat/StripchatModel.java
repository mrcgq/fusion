package ctbrec.sites.stripchat;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.HlsdlDownload;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
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
    volatile transient String lastMasterBody = null;

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
        modelInfo = loadModelInfo();
        return modelInfo;
    }

    private JSONObject loadModelInfo() throws IOException {
        String url = getSite().getBaseUrl() + "/api/front/v2/models/username/" + getName() + "/cam?timezoneOffset=0&triggerRequest=loadCam&uniq=" + getUniq();
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(REFERER, getUrl())
                .header(ORIGIN, getSite().getBaseUrl())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                return jsonResponse;
            } else if (response.code() == 418 || response.code() == 403) {
                // 反爬拦截：回退首页 HTML 内嵌 initialState（带会话 cookie 的同一 HttpClient）
                JSONObject fromHtml = loadModelInfoFromHtml();
                if (fromHtml != null) return fromHtml;
                throw new HttpException(response.code(), response.message());
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private JSONObject loadModelInfoFromHtml() {
        try {
            Request req = new Request.Builder()
                    .url(getUrl())
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(ACCEPT_LANGUAGE, "en-US,en;q=0.9")
                    .build();
            try (Response response = site.getHttpClient().execute(req)) {
                if (!response.isSuccessful()) return null;
                String html = response.body().string();
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\"(?:userId|id)\"\\s*:\\s*(\\d{4,})").matcher(html);
                java.util.regex.Matcher st = java.util.regex.Pattern
                        .compile("\"status\"\\s*:\\s*\"(public|private|p2p|groupShow|idle|off)\"").matcher(html);
                if (m.find()) {
                    JSONObject user = new JSONObject().put("id", Long.parseLong(m.group(1)))
                            .put("status", st.find() ? st.group(1) : "public");
                    return new JSONObject().put("user", new JSONObject().put("user", user));
                }
            }
        } catch (Exception e) {
            log.debug("html fallback failed for {}", getName(), e);
        }
        return null;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        String url = getMasterPlaylistUrl();
        MasterPlaylist masterPlaylist = getMasterPlaylist(url);
        List<StreamSource> streamSources = extractStreamSources(masterPlaylist);
        if (streamSources.isEmpty()) {
            // 现网部分边缘 master 不带 variant（单码率）：直接用 master 当媒体播放列表
            StreamSource single = new StreamSource();
            single.setBandwidth(0);
            single.setHeight(0);
            single.setWidth(0);
            single.setMediaPlaylistUrl(StripchatEdgeResolver.keepQueryString(url));
            streamSources.add(single);
            return streamSources;
        }
        try {
            String originalUrl = url.replace("_auto", "");
            masterPlaylist = getMasterPlaylist(originalUrl);
            for (StreamSource original : extractStreamSources(masterPlaylist)) {
                boolean found = false;
                for (StreamSource source : streamSources) {
                    if (source.getHeight() == original.getHeight()) {
                        found = true;
                        break;
                    }
                }
                if (!found) streamSources.add(original);
            }
        } catch (Exception e) {
            log.warn("Original stream quality not available", e);
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
                src.setMediaPlaylistUrl(StripchatEdgeResolver.keepQueryString(playlist.getUri()));
                log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    private MasterPlaylist getMasterPlaylist(String url) throws IOException, ParseException, PlaylistException {
        log.trace("Loading master playlist {}", url);
        String fixed = StripchatEdgeResolver.normalizeMasterUrl(getSite(), url);
        Request req = new Request.Builder()
                .url(fixed)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                log.trace(body);
                lastMasterBody = body;
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

    private String getMasterPlaylistUrl() throws IOException {
        JSONObject info = getModelInfo();
        if (info.has("user")) {
            JSONObject user = info.getJSONObject("user").getJSONObject("user");
            long id = user.optLong("id");

            boolean saveVR = Config.getInstance().getSettings().stripchatVR;
            boolean isVRStream = user.optBoolean("isVr", false);
            String vrSuffix = (saveVR && isVRStream) ? "_vr" : "";

            String token = "";
            if (info.has("cam")) {
                JSONObject cam = info.getJSONObject("cam");
                if (StringUtil.isNotBlank(cam.optString(KEY_MODEL_TOKEN))) {
                    token = "&aclAuth=" + cam.getString(KEY_MODEL_TOKEN);
                    log.debug("Spy start for {}", getName());
                }
            }
            String master = StripchatEdgeResolver.masterUrl(getSite(), id, !vrSuffix.isEmpty());
            return master + token;
        } else if (info.has("streamName") || info.has("hlsStreamUrl")) {
            // /cam 简化响应（withRecentShow 形态）
            long id = info.optLong("streamName", info.optLong("id", -1));
            if (id > 0) {
                return StripchatEdgeResolver.masterUrl(getSite(), id, false);
            }
            String direct = info.optString("hlsStreamUrl", "");
            if (StringUtil.isNotBlank(direct)) {
                return StripchatEdgeResolver.normalizeMasterUrl(getSite(), direct);
            }
            throw new IOException("Playlist URL not found in cam response");
        } else {
            throw new IOException("Playlist URL not found");
        }
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
                    StreamSource best = sources.getLast();
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
        JSONObject info = getModelInfo();
        JSONObject user = info.getJSONObject("user").getJSONObject("user");
        long id = user.optLong("id");

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
        JSONObject info = getModelInfo();
        JSONObject user = info.getJSONObject("user").getJSONObject("user");
        long id = user.optLong("id");
        JSONArray favoriteIds = new JSONArray().put(id);
        favoriteIds.put(id);

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

    @Override
    public boolean exists() throws IOException {
        JSONObject jsonResponse = getModelInfo();
        if (jsonResponse.optString("error").equals("Not Found")) {
            log.info("Model not found: {}", getName());
            return false;
        }
        if (jsonResponse.has("user")) {
            JSONObject user = jsonResponse.getJSONObject("user");
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
        } else {
            return new StripchatFfmpegHlsDownload(getSite().getHttpClient());
        }
    }

    private String getUniq() {
        return ((Stripchat) getSite()).getUniq();
    }
}

