package ctbrec.sites.mfc;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.HttpHeaderFactoryImpl;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

public class MyFreeCamsModel extends AbstractModel {

    private static final Logger LOG = LoggerFactory.getLogger(MyFreeCamsModel.class);

    @Getter
    @Setter
    private int uid = -1; // undefined
    @Getter
    @Setter
    private String streamUrl;
    @Getter
    @Setter
    private double camScore;
    @Getter
    @Setter
    private int viewerCount;
    private ctbrec.sites.mfc.State state;
    private int[] resolution = new int[2];

    /**
     * This constructor exists only for deserialization. Please don't call it directly
     */
    @SuppressWarnings("unused")
    public MyFreeCamsModel() {
    }

    MyFreeCamsModel(MyFreeCams site) {
        this.site = site;
    }

    @Override
    public boolean isOnline() throws IOException, ExecutionException, InterruptedException {
        MyFreeCamsClient.getInstance().update(this);
        return state == ctbrec.sites.mfc.State.ONLINE;
    }

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            List<ctbrec.Model> searchResult = MyFreeCamsClient.getInstance().search(getName());
            if (!searchResult.isEmpty()) {
                MyFreeCamsModel m = (MyFreeCamsModel) searchResult.get(0);
                this.state = m.state;
                this.onlineState = m.getOnlineState(true);
            }
        }
        return isOnline();
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        if (state == null) {
            return State.UNCHECKED;
        }

        if (!failFast) {
            MyFreeCamsClient.getInstance().update(this);
            try {
                isOnline(true);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        return switch (state) {
            case ONLINE, RECORDING -> State.ONLINE;
            case AWAY -> State.AWAY;
            case PRIVATE -> State.PRIVATE;
            case GROUP_SHOW -> State.GROUP;
            case OFFLINE, CAMOFF, UNKNOWN -> State.OFFLINE;
            default -> {
                LOG.debug("State {} is not mapped", this.state);
                yield State.UNKNOWN;
            }
        };
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        return getStreamSourceProvider().getStreamSources(updateStreamUrl());
    }

    private StreamSourceProvider getStreamSourceProvider() {
        return new HlsStreamSourceProvider(getSite().getHttpClient());
    }

    private String updateStreamUrl() {
        if (streamUrl == null) {
            MyFreeCams mfc = (MyFreeCams) getSite();
            mfc.getClient().update(this);
        }
        return streamUrl;
    }

    @Override
    public void invalidateCacheEntries() {
        resolution = null;
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        String tipUrl = MyFreeCams.baseUrl + "/php/tip.php";
        String initUrl = tipUrl + "?request=tip&username=" + getName() + "&broadcaster_id=" + getUid();
        Request req = new Request.Builder()
                .url(initUrl)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(CONNECTION, KEEP_ALIVE)
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (resp.isSuccessful()) {
                String page = Objects.requireNonNull(resp.body(), "HTTP response is null").string();
                Element hiddenInput = HtmlParser.getTag(page, "input[name=token]");
                String token = hiddenInput.attr("value");
                if (!Objects.equals(System.getenv("CTBREC_DEV"), "1")) {
                    RequestBody body = new FormBody.Builder()
                            .add("token", token)
                            .add("broadcaster_id", Integer.toString(uid))
                            .add("tip_value", Integer.toString(tokens.intValue()))
                            .add("submit_tip", "1")
                            .add("anonymous", "")
                            .add("public", "1")
                            .add("public_comment", "1")
                            .add("hide_amount", "0")
                            .add("silent", "")
                            .add("comment", "")
                            .add("mode", "")
                            .add("submit", " Confirm & Close Window")
                            .build();
                    req = new Request.Builder()
                            .url(tipUrl)
                            .post(body)
                            .header(ACCEPT, "*/*")
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header(CONNECTION, KEEP_ALIVE)
                            .header(REFERER, initUrl)
                            .build();
                    try (Response response = site.getHttpClient().execute(req)) {
                        if (!response.isSuccessful()) {
                            throw new HttpException(response.code(), response.message());
                        }
                    }
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        if (!failFast && streamUrl != null) {
            try {
                List<StreamSource> streamSources = getStreamSources();
                Collections.sort(streamSources);
                StreamSource best = streamSources.get(streamSources.size() - 1);
                resolution = new int[]{best.getWidth(), best.getHeight()};
            } catch (JAXBException | ParseException | PlaylistException e) {
                LOG.warn("Couldn't determine stream resolution - {}", e.getMessage());
            } catch (ExecutionException | IOException e) {
                LOG.error("Couldn't determine stream resolution", e);
            }
        }
        return resolution;
    }

    public boolean isNew() {
        MyFreeCams mfc = (MyFreeCams) getSite();
        SessionState sessionState = mfc.getClient().getSessionState(this);
        return ofNullable(sessionState).map(SessionState::getM).map(Model::getNewModel).orElse(0) == 1;
    }

    public void setMfcState(ctbrec.sites.mfc.State state) {
        this.state = state;
    }

    @Override
    public void setName(String name) {
        if (getName() != null && name != null && !getName().equals(name)) {
            LOG.debug("Model name changed {} -> {}", getName(), name);
            setUrl("https://profiles.myfreecams.com/" + name);
        }
        super.setName(name);
    }

    public void update(SessionState state, String streamUrl) {
        uid = state.getUid();
        setName(state.getNm());
        setMfcState(ctbrec.sites.mfc.State.of(state.getVs()));
        setStreamUrl(streamUrl);
        setCamScore(ofNullable(state.getM()).map(Model::getCamscore).orElse(0.0));


        // preview
        String uidString = state.getUid().toString();
        String uidStart = uidString.substring(0, 3);
        String previewUrl = "https://img.mfcimg.com/photos2/" + uidStart + '/' + uidString + "/avatar.300x300.jpg";
        if (MyFreeCamsModel.this.state == ctbrec.sites.mfc.State.ONLINE) {
            try {
                previewUrl = getLivePreviewUrl(state);
            } catch (Exception e) {
                LOG.error("Couldn't get live preview. Falling back to avatar", e);
            }
        }
        setPreview(previewUrl);

        // tags
        ofNullable(state.getM()).map(Model::getTags).ifPresent(tags -> setTags(new ArrayList<>(tags)));

        // description
        ofNullable(state.getM()).map(Model::getTopic).ifPresent(topic -> setDescription(URLDecoder.decode(topic, UTF_8)));

        viewerCount = ofNullable(state.getM()).map(Model::getRc).orElse(0);
    }

    private String getLivePreviewUrl(SessionState state) {
        String previewUrl;
        int userChannel = 100000000 + state.getUid();
        int camserv = state.getU().getCamserv();
        String server;
        ServerConfig sc = ((MyFreeCams) site).getClient().getServerConfig();
        String phase = state.getU().getPhase();
        if (sc.isOnNgServer(state)) {
            server = sc.ngVideoServers.get(Integer.toString(camserv));
            camserv = toCamServ(server);
            previewUrl = toPreviewUrl(camserv, phase, userChannel);
        } else if (sc.isOnWzObsVideoServer(state)) {
            server = sc.wzobsServers.get(Integer.toString(camserv));
            camserv = toCamServ(server);
            previewUrl = toPreviewUrl(camserv, phase, userChannel);
        } else if (sc.isOnHtml5VideoServer(state)) {
            server = sc.h5Servers.get(Integer.toString(camserv));
            camserv = toCamServ(server);
            previewUrl = toPreviewUrl(camserv, userChannel);
        } else {
            if (camserv > 500) camserv -= 500;
            previewUrl = toPreviewUrl(camserv, userChannel);
        }
        return previewUrl;
    }

    private String toPreviewUrl(int camserv, String phase, int userChannel) {
        return "https://snap.mfcimg.com/snapimg/" + camserv + "/320x240/mfc_" + phase + '_' + userChannel;
    }

    private String toPreviewUrl(int camserv, int userChannel) {
        return "https://snap.mfcimg.com/snapimg/" + camserv + "/320x240/mfc_" + userChannel;
    }

    private int toCamServ(String server) {
        return Integer.parseInt(server.replaceAll("\\D+", ""));
    }

    @Override
    public boolean follow() {
        return ((MyFreeCams) site).getClient().follow(getUid());
    }

    @Override
    public boolean unfollow() {
        return ((MyFreeCams) site).getClient().unfollow(getUid());
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        uid = Integer.parseInt(data.get("uid"));
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        data.put("uid", Integer.toString(uid));
    }

    @Override
    public RecordingProcess createDownload() {
        if (streamUrl == null) {
            updateStreamUrl();
        }
        return super.createDownload();
    }

    @Override
    public HttpHeaderFactory getHttpHeaderFactory() {
        HttpHeaderFactoryImpl fac = new HttpHeaderFactoryImpl();
        Map<String, String> headers = new HashMap<>();
        headers.put(ACCEPT, "*/*");
        headers.put(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage());
        headers.put(CONNECTION, "close");
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
