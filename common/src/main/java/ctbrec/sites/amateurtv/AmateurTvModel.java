package ctbrec.sites.amateurtv;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.FfmpegHlsDownload;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;

public class AmateurTvModel extends AbstractModel {

    private static final Logger LOG = LoggerFactory.getLogger(AmateurTvModel.class);
    private transient JSONArray qualities = new JSONArray();
    private int[] resolution = new int[2];

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            JSONObject json = getModelInfo();
            setOnlineState(OFFLINE);

            boolean online = json.optString("status").equalsIgnoreCase("online");
            if (online) setOnlineState(ONLINE);

            boolean brb = json.optBoolean("brb");
            if (brb) setOnlineState(AWAY);

            boolean privateChat = json.optString("privateChatStatus").equalsIgnoreCase("exclusive_private");
            if (privateChat) setOnlineState(PRIVATE);
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
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        List<StreamSource> streamSources = new ArrayList<>();
        String mediaPlaylistUrl = getMasterPlaylistUrl();
        qualities.forEach(item -> {
            String value = (String) item;
            String[] res = value.split("x");
            StreamSource src = new StreamSource();
            src.setMediaPlaylistUrl(MessageFormat.format("{0}&variant={1}", mediaPlaylistUrl, res[1]));
            src.setWidth(Integer.parseInt(res[0]));
            src.setHeight(Integer.parseInt(res[1]));
            src.setBandwidth(0);
            streamSources.add(src);
        });
        return streamSources;
    }

    private String getMasterPlaylistUrl() throws IOException {
        JSONObject json = getModelInfo();
        JSONObject videoTech = json.getJSONObject("videoTechnologies");
        qualities = json.getJSONArray("qualities");
        return videoTech.getString("fmp4-hls");
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = new int[2];
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        // not supported
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (!failFast) {
            try {
                List<StreamSource> sources = getStreamSources();
                if (!sources.isEmpty()) {
                    StreamSource best = sources.get(0);
                    resolution = new int[]{best.getWidth(), best.getHeight()};
                }
            } catch (IOException | ParseException | PlaylistException | JAXBException e) {
                throw new ExecutionException(e);
            }
        }
        return resolution;
    }

    @Override
    public boolean follow() throws IOException {
        String url = getSite().getBaseUrl() + "/v3/user/follow";
        return followUnfollow(url);
    }

    @Override
    public boolean unfollow() throws IOException {
        String url = getSite().getBaseUrl() + "/v3/user/unfollow";
        return followUnfollow(url);
    }

    private boolean followUnfollow(String url) throws IOException {
        if (!getSite().login()) {
            throw new IOException("Not logged in");
        }

        LOG.debug("Calling {}", url);
        RequestBody body = new FormBody.Builder()
                .add("username", getName())
                .build();
        Request req = new Request.Builder()
                .url(url)
                .method("POST", body)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getUrl())
                .header(ORIGIN, getSite().getBaseUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String msg = Objects.requireNonNull(resp.body()).string();
                JSONObject json = new JSONObject(msg);
                if (Objects.equals(json.getString("result"), "OK")) {
                    LOG.debug("Follow/Unfollow -> {}", msg);
                    return true;
                } else {
                    LOG.debug(msg);
                    throw new IOException("Response was " + msg);
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    private JSONObject getModelInfo() throws IOException {
        String url = AmateurTv.BASE_URL + "/v3/readmodel/show/" + getName() + "/es";
        Request req = new Request.Builder().url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, "en")
                .header(REFERER, getSite().getBaseUrl() + '/' + getName())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject jsonResponse = new JSONObject(response.body().string());
                return jsonResponse;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public RecordingProcess createDownload() {
        return new FfmpegHlsDownload(getSite().getHttpClient());
    }
}
