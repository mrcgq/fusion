package ctbrec.sites.secretfriends;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.jsoup.nodes.Element;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.Model.State.ONLINE;
import static ctbrec.io.HttpConstants.*;

public class SecretFriendsModel extends AbstractModel {
    private int[] resolution = new int[]{0, 0};
    private static final Random RNG = new Random();

    private static final String H5LIVE = "h5live";
    private static final String SECURITY = "security";

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            String url = SecretFriends.BASE_URI + "/friend/bio/" + getName();
            Request req = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                    .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                    .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .header(REFERER, getUrl())
                    .build();
            try (Response response = site.getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                    Element wrapper = HtmlParser.getTag(body, "div[class~=model-wrapper]");
                    SecretFriendsModel parsedModel = SecretFriendsModelParser.parse((SecretFriends) getSite(), wrapper);
                    setName(parsedModel.getName());
                    setUrl(parsedModel.getUrl());
                    setPreview(parsedModel.getPreview());
                    setOnlineState(parsedModel.getOnlineState(true));
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
        return onlineState == ONLINE;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        String bioPage = loadBioPage();
        String streamName = getStreamName(bioPage);
        String streamId = getStreamId(bioPage);
        JSONObject token = getToken(streamName);

        String stream = streamName + "?host=www.secretfriends.com"
                + "&startAt=" + Instant.now().getEpochSecond()
                + "&userId=null&ip=0.0.0.0&cSessionId=guestKey"
                + "&streamId=" + streamId
                + "&groupId=null"
                + "&userAgent=" + Config.getInstance().getSettings().httpUserAgent;

        HttpUrl wsUrl = new HttpUrl.Builder()
                .scheme("https")
                .host("bintu-splay.nanocosmos.de")
                .addPathSegments("h5live/authstream")
                .addQueryParameter("url", "rtmp://bintu-splay.nanocosmos.de/splay")
                .addQueryParameter("stream", stream)
                .addQueryParameter("cid", String.valueOf(RNG.nextInt(899000) + 100000))
                .addQueryParameter("pid", String.valueOf(RNG.nextLong() + 10_000_000_000L))
                .addQueryParameter("token", token.getJSONObject(H5LIVE).getJSONObject(SECURITY).getString("token"))
                .addQueryParameter("expires", token.getJSONObject(H5LIVE).getJSONObject(SECURITY).getString("expires"))
                .addQueryParameter("options", token.getJSONObject(H5LIVE).getJSONObject(SECURITY).getString("options"))
                .build();

        StreamSource src = new StreamSource();
        src.setWidth(1280);
        src.setHeight(720);
        src.setMediaPlaylistUrl(wsUrl.toString());
        return Collections.singletonList(src);
    }

    private String getStreamId(String bioPage) throws IOException {
        Pattern p = Pattern.compile("app.configure\\((.*?)\\);");
        Matcher m = p.matcher(bioPage);
        if (m.find()) {
            JSONObject appConfig = new JSONObject(m.group(1));
            return appConfig.getJSONObject("page").getJSONObject("user").getString("id");
        } else {
            throw new IOException("app configuration not found in HTML");
        }
    }

    private JSONObject getToken(String streamName) throws IOException {
        String url = SecretFriends.BASE_URI + "/nano/generateToken?streamName=" + streamName;
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(REFERER, getUrl())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                return new JSONObject(body);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private String loadBioPage() throws IOException {
        String url = SecretFriends.BASE_URI + "/friends/" + getName();
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(REFERER, getUrl())
                .build();
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                return Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private String getStreamName(String bioPage) throws IOException {
        Pattern p = Pattern.compile("'streamName'\\s*:\\s*\"(.*?)\",");
        Matcher m = p.matcher(bioPage);
        if (m.find()) {
            return m.group(1);
        } else {
            throw new IOException("Stream name not found in HTML");
        }
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = new int[]{0, 0};
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
                    StreamSource best = sources.get(sources.size() - 1);
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
        return false;
    }

    @Override
    public boolean unfollow() throws IOException {
        return false;
    }

    @Override
    public RecordingProcess createDownload() {
        return new SecretFriendsWebrtcDownload(getSite().getHttpClient());
    }
}
