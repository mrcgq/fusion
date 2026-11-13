package ctbrec.sites.dreamcam;

import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.InvalidPlaylistException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;

public class DreamcamModel extends AbstractModel {

    private static final Logger LOG = LoggerFactory.getLogger(DreamcamModel.class);
    private static final String API_URL = "https://bss.dreamcamtrue.com";
    private int[] resolution = new int[2];
    private JSONObject modelInfo;
    private boolean VRMode = false;

    private transient Instant lastInfoRequest = Instant.EPOCH;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            try {
                JSONObject json = getModelInfo();
                mapOnlineState(json.optString("broadcastStatus"));
            } catch (Exception e) {
                setOnlineState(OFFLINE);
            }
        }
        return onlineState == ONLINE;
    }

    private void mapOnlineState(String status) {
        switch (status) {
            case "public" -> setOnlineState(ONLINE);
            case "private" -> setOnlineState(PRIVATE);
            case "offline" -> setOnlineState(OFFLINE);
            default -> setOnlineState(OFFLINE);
        }
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (failFast && onlineState != UNKNOWN && onlineState != UNCHECKED) {
            return onlineState;
        } else {
            try {
                JSONObject json = getModelInfo();
                mapOnlineState(json.optString("broadcastStatus"));
            } catch (Exception ex) {
                setOnlineState(OFFLINE);
            }
            return onlineState;
        }
    }

    @Override
    public List<StreamSource> getStreamSources() throws InvalidPlaylistException {
        List<StreamSource> sources = new ArrayList<>();
        try {
            StreamSource src = new StreamSource();
            src.setMediaPlaylistUrl(getPlaylistUrl());
            sources.add(src);
        } catch (Exception e) {
            LOG.error("Can not get stream sources for {}: {}", getName(), e.getMessage());
            throw new InvalidPlaylistException(e.getMessage());
        }
        return sources;
    }

    private String getPlaylistUrl() throws IOException, InvalidPlaylistException {
        JSONObject json = getModelInfo();
        String mediaUrl = "";
        if (json.has("streams")) {
            JSONArray streams = json.getJSONArray("streams");
            for (int i = 0; i < streams.length(); i++) {
                JSONObject s = streams.getJSONObject(i);
                if (s.has("streamType") && s.has("url")) {
                    String streamType = s.getString("streamType");
                    if (streamType.equals("video2D")) {
                        mediaUrl = s.optString("url");
                        LOG.trace("PlaylistUrl for {}: {}", getName(), mediaUrl);
                    }
                }
            }
        }
        if (StringUtil.isBlank(mediaUrl)) {
            throw new InvalidPlaylistException("Playlist has no media");
        }
        return mediaUrl;
    }

    public String getWsUrl() throws IOException {
        JSONObject json = getModelInfo();
        return json.optString("streamUrl").replace("fmp4s://", "wss://");
    }

    public String getChatId() throws IOException {
        JSONObject json = getModelInfo();
        return json.optString("roomChatId");
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        return new int[2];
    }

    private JSONObject getModelInfo() throws IOException {
        if (Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            modelInfo = Optional.ofNullable(modelInfo).orElse(loadModelInfo());
        } else {
            modelInfo = loadModelInfo();
        }
        return modelInfo;
    }

    private JSONObject loadModelInfo() throws IOException {
        lastInfoRequest = Instant.now();
        String url = MessageFormat.format(API_URL + "/api/clients/v1/broadcasts/models/{0}?partnerId=dreamcam_oauth2&show-hidden=true&stream-types=video2D,video3D", getName());
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, getUrl())
                .header(ORIGIN, getSite().getBaseUrl())
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                return json;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public String getPreviewURL() throws IOException {
        JSONObject json = getModelInfo();
        return json.optString("modelProfilePhotoUrl");
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
    public void receiveTip(Double tokens) throws IOException {
        // not implemented
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = new int[]{0, 0};
        lastInfoRequest = Instant.EPOCH;
        modelInfo = null;
    }

    @Override
    public RecordingProcess createDownload() {
        if (Config.getInstance().getSettings().dreamcamVR) {
            return new DreamcamDownload(getSite().getHttpClient());
        } else {
            return new MergedFfmpegHlsDownload(getSite().getHttpClient());
        }
    }
}
