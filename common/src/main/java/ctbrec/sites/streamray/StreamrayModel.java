package ctbrec.sites.streamray;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.FfmpegHlsDownload;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;
import static ctbrec.sites.streamray.Streamray.API_URL;

@Slf4j
public class StreamrayModel extends AbstractModel {

    private String status = null;

    @Getter
    @Setter
    private String gender = null;

    @Setter
    private LocalDate regDate = LocalDate.EPOCH;

    @Getter
    @Setter
    private boolean favorite = false;

    private transient JSONObject modelInfo;
    private transient Instant lastInfoRequest = Instant.EPOCH;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            try {
                JSONObject json = getModelInfo();
                if (json.has("online")) {
                    status = json.optString("online");
                    setOnlineState(((Streamray) site).mapOnlineState(status));
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
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        List<StreamSource> sources = new ArrayList<>();
        try {
            String url = getMasterPlaylistUrl();
            StreamSource src = new StreamSource();
            src.setMediaPlaylistUrl(url);
            src.setHeight(0);
            src.setWidth(0);
            src.setBandwidth(0);
            sources.add(src);
        } catch (IOException e) {
            log.error("Can not get stream sources for {}", getName());
        }
        return sources;
    }

    private String getMasterPlaylistUrl() throws IOException {
        JSONObject json = getModelInfo();
        String mpp = json.getString("mpp");
        String lname = getName().toLowerCase();
        return MessageFormat.format("https://stream14.cams.com/h5live/http/playlist.m3u8?url=rtmp%3A%2F%2F{0}%3A1935%2Fcams%2F{1}%3Fcams%2F{1}_720p&stream={2}", mpp, lname, getName());
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        return new int[]{0, 0};
    }

    private JSONObject getModelInfo() throws IOException {
        if (modelInfo == null || Duration.between(lastInfoRequest, Instant.now()).getSeconds() < 5) {
            modelInfo = loadModelInfo();
        }
        return modelInfo;
    }

    private JSONObject loadModelInfo() throws IOException {
        lastInfoRequest = Instant.now();
        String url = API_URL + "/models/stream/" + getName() + "/";
        Request req = new Request.Builder().url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, "en")
                .header(REFERER, getSite().getBaseUrl() + '/' + getName())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                return new JSONObject(response.body().string());
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private JSONObject loadModelProfileInfo() throws IOException {
        String url = API_URL + "/models/new/" + getName() + "/";
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

    @Override
    public boolean follow() throws IOException {
        JSONObject info = loadModelProfileInfo();
        String id = info.optString("id");
        if (StringUtil.isBlank(id)) {
            log.debug("Can't get model id");
            return false;
        }
        String token = ((StreamrayHttpClient) site.getHttpClient()).getUserToken();
        if (StringUtil.isBlank(token)) {
            log.debug("Not logged in");
            return false;
        }
        String url = API_URL + "/favorites/member_favorites/";
        JSONObject json = new JSONObject().put("model", id);
        RequestBody requestBody = RequestBody.create(json.toString(), MediaType.parse("application/json"));
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, "en")
                .header(REFERER, getSite().getBaseUrl() + '/' + getName())
                .header(ORIGIN, getSite().getBaseUrl())
                .header(AUTHORIZATION, "Bearer " + token)
                .post(requestBody)
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                return true;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public boolean unfollow() throws IOException {
        JSONObject info = loadModelProfileInfo();
        String id = info.optString("id");
        if (StringUtil.isBlank(id)) {
            log.debug("Can't get model id");
            return false;
        }
        String token = ((StreamrayHttpClient) site.getHttpClient()).getUserToken();
        if (StringUtil.isBlank(token)) {
            log.debug("Not logged in");
            return false;
        }
        String url = API_URL + "/favorites/member_favorites/" + id + "/";
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "en")
                .header(REFERER, getSite().getBaseUrl() + '/' + getName())
                .header(ORIGIN, getSite().getBaseUrl())
                .header(AUTHORIZATION, "Bearer " + token)
                .delete()
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                return true;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        // not implemented
    }

    @Override
    public void invalidateCacheEntries() {
        status = null;
        lastInfoRequest = Instant.EPOCH;
        modelInfo = null;
    }

    public boolean isNew() {
        return ChronoUnit.DAYS.between(this.regDate, LocalDate.now()) < 30;
    }

}
