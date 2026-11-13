package ctbrec.sites.winktv;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class WinkTvModel extends AbstractModel {

    private static final String KEY_MEDIA = "media";
    private int[] resolution = new int[]{0, 0};

    @Getter
    @Setter
    private boolean adult = false;
    private transient JSONObject modelInfo;

    private transient Instant lastInfoRequest = Instant.EPOCH;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            try {
                JSONObject json = getModelInfo();
                if (json.has(KEY_MEDIA)) {
                    JSONObject media = json.getJSONObject(KEY_MEDIA);
                    boolean isLive = media.optBoolean("isLive");
                    String meType = media.optString("type");
                    if (isLive && meType.equals("free")) {
                        setOnlineState(ONLINE);
                    } else {
                        setOnlineState(PRIVATE);
                    }
                } else {
                    setOnlineState(OFFLINE);
                }
            } catch (Exception e) {
                setOnlineState(UNKNOWN);
            }
        }
        return onlineState == ONLINE;
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (!failFast || onlineState == UNKNOWN || onlineState == UNCHECKED) {
            try {
                onlineState = isOnline(true) ? ONLINE : OFFLINE;
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
        String url = getMasterPlaylistUrl();
        MasterPlaylist masterPlaylist = getMasterPlaylist(url);
        List<StreamSource> streamSources = extractStreamSources(masterPlaylist);
        return streamSources;
    }

    private List<StreamSource> extractStreamSources(MasterPlaylist masterPlaylist) {
        List<StreamSource> sources = new ArrayList<>();
        for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
            if (playlist.hasStreamInfo()) {
                StreamSource src = new StreamSource();
                src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                src.setHeight(playlist.getStreamInfo().getResolution().height);
                src.setWidth(playlist.getStreamInfo().getResolution().width);
                src.setMediaPlaylistUrl(playlist.getUri());
                log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    private MasterPlaylist getMasterPlaylist(String url) throws IOException, ParseException, PlaylistException {
        log.trace("Loading master playlist {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
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
        JSONObject json = getModelInfo();
        JSONObject info = json.getJSONObject("bjInfo");
        long userIdx = info.optLong("idx");
        String url = "https://api.winktv.co.kr/v1/live/play";
        FormBody body = new FormBody.Builder()
                .add("action", "watch")
                .add("userIdx", String.valueOf(userIdx))
                .add("password", "")
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, getUrl())
                .header(ORIGIN, getSite().getBaseUrl())
                .post(body)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                JSONObject playlist = jsonResponse.getJSONObject("PlayList");
                JSONObject hls = playlist.getJSONArray("hls").getJSONObject(0);
                String hlsUrl = hls.optString("url");
                return hlsUrl;
            } else {
                log.debug("Error while get master playlist url for {}: {}", getName(), response.body().string());
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (!failFast) {
            try {
                List<StreamSource> sources = getStreamSources();
                if (!sources.isEmpty()) {
                    StreamSource best = sources.get(sources.size() - 1);
                    resolution = new int[]{best.getWidth(), best.getHeight()};
                }
            } catch (IOException | ParseException | PlaylistException e) {
                throw new ExecutionException(e);
            }
        }
        return resolution;
    }

    private JSONObject getModelInfo() throws IOException {
        if (modelInfo == null || Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            lastInfoRequest = Instant.now();
            modelInfo = loadModelInfo();
        }
        return modelInfo;
    }

    private JSONObject loadModelInfo() throws IOException {
        String url = "https://api.winktv.co.kr/v1/member/bj";
        FormBody body = new FormBody.Builder()
                .add("userId", getName())
                .add("info", KEY_MEDIA)
                .build();
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, getUrl())
                .header(ORIGIN, getSite().getBaseUrl())
                .post(body)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                return jsonResponse;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
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
        return new MergedFfmpegHlsDownload(getSite().getHttpClient());
    }
}
