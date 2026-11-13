package ctbrec.sites.jasmin;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.io.HttpConstants.*;

public class LiveJasminModel extends AbstractModel {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminModel.class);
    private String id;
    private boolean online = false;
    private int[] resolution;

    private final Random rng = new Random();

    private transient LiveJasminModelInfo modelInfo;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            loadModelInfo();
        }
        return online;
    }

    protected void loadModelInfo() throws IOException {
        Request req = new Request.Builder().url(LiveJasmin.baseUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_ENCODING, "deflate")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getSite().getBaseUrl() + "/")
                .header(ORIGIN, getSite().getBaseUrl())
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            // do nothing we just want the cookies
            LOG.debug("Initial request succeeded: {} - {}", response.isSuccessful(), response.code());
        }

        String url = LiveJasmin.baseUrl + "/en/flash/get-performer-details/" + getName();
        req = new Request.Builder().url(url)
                .header(USER_AGENT, "Mozilla/5.0 (iPhone; CPU OS 10_14 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/11.1.1 Mobile/14E304 Safari/605.1.15")
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_ENCODING, "deflate")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, getSite().getBaseUrl() + "/")
                .header(ORIGIN, getSite().getBaseUrl())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                LOG.trace(json.toString(2));
                if (json.optBoolean("success")) {
                    JSONObject data = json.getJSONObject("data");
                    modelInfo = new LiveJasminModelInfo.LiveJasminModelInfoBuilder()
                            .sbIp(data.optString("sb_ip", null))
                            .sbHash(data.optString("sb_hash", null))
                            .sessionId("m12345678901234567890123456789012")
                            .jsm2session(getSite().getHttpClient().getCookiesByName("session").get(0).value())
                            .performerId(data.optString("performer_id", getName()))
                            .displayName(data.optString("display_name", getName()))
                            .clientInstanceId(randomClientInstanceId())
                            .status(data.optInt("status", -1))
                            .build();
                    if (data.has("channelsiteurl")) {
                        setUrl(LiveJasmin.baseUrl + data.getString("channelsiteurl"));
                    }
                    onlineState = mapStatus(modelInfo.getStatus());
                    online = onlineState == State.ONLINE
                            && StringUtil.isNotBlank(modelInfo.getSbIp())
                            && StringUtil.isNotBlank(modelInfo.getSbHash());
                    LOG.debug("{} - status:{} {} {} {} {}", getName(), online, onlineState, Arrays.toString(resolution), getUrl(), id);
                } else {
                    throw new IOException("Response was not successful: " + body);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private String randomClientInstanceId() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 32; i++) {
            sb.append(rng.nextInt(9) + 1);
        }
        return sb.toString();
    }

    public static State mapStatus(int status) {
        switch (status) {
            case 0 -> {
                return State.OFFLINE;
            }
            case 1 -> {
                return State.ONLINE;
            }
            case 2, 3 -> {
                return State.PRIVATE;
            }
            default -> {
                LOG.debug("Unkown state {}", status);
                return State.UNKNOWN;
            }
        }
    }

    @Override
    public void setOnlineState(State status) {
        super.setOnlineState(status);
        online = status == State.ONLINE;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        loadModelInfo();

        String websocketUrlTemplate = "wss://dss-relay-{ipWithDashes}.dditscdn.com/memberChat/jasmin{modelName}{sb_hash}?random={clientInstanceId}";
        String websocketUrl = websocketUrlTemplate
                .replace("{ipWithDashes}", modelInfo.getSbIp().replace('.', '-'))
                .replace("{modelName}", getName())
                .replace("{sb_hash}", modelInfo.getSbHash())
                .replace("{clientInstanceId}", modelInfo.getClientInstanceId());
        modelInfo.setWebsocketUrl(websocketUrl);

        LiveJasminStreamRegistration liveJasminStreamRegistration = new LiveJasminStreamRegistration(site, modelInfo);
        List<StreamSource> streamSources = liveJasminStreamRegistration.getStreamSources();
        Collections.sort(streamSources);
        return streamSources;
    }

    @Override
    public void invalidateCacheEntries() {
        // noop
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        LiveJasminTippingWebSocket tippingSocket = new LiveJasminTippingWebSocket(site.getHttpClient());
        try {
            tippingSocket.sendTip(this, Config.getInstance(), tokens);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (resolution == null) {
            if (failFast) {
                return new int[2];
            }
            try {
                loadModelInfo();
            } catch (IOException e) {
                throw new ExecutionException(e);
            }
        }
        return resolution;
    }

    @Override
    public boolean follow() throws IOException {
        return follow(true);
    }

    @Override
    public boolean unfollow() throws IOException {
        return follow(false);
    }

    private boolean follow(boolean follow) throws IOException {
        if (id == null) {
            loadModelInfo();
        }

        String sessionId = ((LiveJasminHttpClient) site.getHttpClient()).getSessionId();
        String url;
        if (follow) {
            url = site.getBaseUrl() + "/en/free/favourite/add-favourite?session=" + sessionId + "&performerId=" + id;
        } else {
            url = site.getBaseUrl() + "/en/free/favourite/delete-favourite?session=" + sessionId + "&performerId=" + id;
        }
        Request request = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, "*/*")
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, getUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = site.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                return json.optString("status").equalsIgnoreCase("ok");
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        id = data.get("id");
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        if (id == null) {
            try {
                loadModelInfo();
            } catch (IOException e) {
                LOG.error("Couldn't load model ID for {}. This can cause problems with saving / loading the model", getName());
            }
        }
        data.put("id", id);
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    @Override
    public RecordingProcess createDownload() {
        return new LiveJasminWebrtcDownload(getSite().getHttpClient());
    }
}
