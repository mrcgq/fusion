package ctbrec.sites.showup;

import com.google.common.base.Objects;
import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.HttpHeaderFactoryImpl;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import lombok.Getter;
import lombok.Setter;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.*;

public class ShowupModel extends AbstractModel {
    private static final Random RNG = new Random();

    @Getter
    @Setter
    private String uid;
    @Getter
    @Setter
    private String streamId;
    @Getter
    @Setter
    private String streamTranscoderAddr;
    private final int[] resolution = new int[2];

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            boolean foundInList = false;
            List<Model> modelList = getShowupSite().getModelList();
            for (Model model : modelList) {
                ShowupModel m = (ShowupModel) model;
                if (Objects.equal(m.getName(), getName())) {
                    setOnlineState(m.getOnlineState(false));
                    setStreamId(m.getStreamId());
                    setStreamTranscoderAddr(m.getStreamTranscoderAddr());
                    setDescription(m.getDescription());
                    setPreview(m.getPreview());
                    foundInList = true;
                }
            }
            if (!foundInList) {
                setOnlineState(State.OFFLINE);
            }
        }
        State state = getOnlineState(true);
        return state == ONLINE || state == GROUP || state == PRIVATE;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        StreamSource src = new StreamSource();
        src.setWidth(480);
        src.setHeight(360);

        if (streamId == null || streamTranscoderAddr == null) {
            List<Model> modelList = getShowupSite().getModelList();
            for (Model model : modelList) {
                ShowupModel m = (ShowupModel) model;
                if (Objects.equal(m.getName(), getName())) {
                    streamId = m.getStreamId();
                    streamTranscoderAddr = m.getStreamTranscoderAddr();
                }
            }
        }

        int cdnHost = 1 + RNG.nextInt(5);
        int cid = 100_000 + RNG.nextInt(900_000);
        long pid = 10_000_000_000L + RNG.nextInt();
        String urlTemplate = "https://cdn-e0{0}.showup.tv/h5live/http/playlist.m3u8?url=rtmp%3A%2F%2F{1}%3A1935%2Fwebrtc&stream={2}_aac&cid={3}&pid={4}";
        src.setMediaPlaylistUrl(MessageFormat.format(urlTemplate, cdnHost, streamTranscoderAddr, streamId, cid, pid));
        List<StreamSource> sources = new ArrayList<>();
        sources.add(src);
        return sources;
    }

    @Override
    public void invalidateCacheEntries() {
        // noop
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        // noop
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
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

    private Showup getShowupSite() {
        return (Showup) getSite();
    }

    @Override
    public RecordingProcess createDownload() {
        return new ShowupWebrtcDownload(getSite().getHttpClient());
    }

    @Override
    public HttpHeaderFactory getHttpHeaderFactory() {
        HttpHeaderFactoryImpl fac = new HttpHeaderFactoryImpl();
        Map<String, String> headers = new HashMap<>();
        headers.put(ACCEPT, "*/*");
        headers.put(ACCEPT_LANGUAGE, "pl");
        headers.put(CONNECTION, KEEP_ALIVE);
        if (getSite() != null) {
            headers.put(REFERER, getSite().getBaseUrl());
        }
        headers.put(USER_AGENT, Config.getInstance().getSettings().httpUserAgent);
        fac.setMasterPlaylistHeaders(headers);
        fac.setSegmentPlaylistHeaders(headers);
        fac.setSegmentHeaders(headers);
        return fac;
    }

    public String getWebRtcUrl() throws IOException {
        if (streamId == null || streamTranscoderAddr == null) {
            List<Model> modelList = getShowupSite().getModelList();
            for (Model model : modelList) {
                ShowupModel m = (ShowupModel) model;
                if (Objects.equal(m.getName(), getName())) {
                    streamId = m.getStreamId();
                    streamTranscoderAddr = m.getStreamTranscoderAddr();
                }
            }
        }

        int cdnHost = 1 + RNG.nextInt(5);
        int cid = 100_000 + RNG.nextInt(900_000);
        long pid = 10_000_000_000L + RNG.nextInt();
        String urlTemplate = "https://cdn-e0{0}.showup.tv/h5live/stream/?url=rtmp%3A%2F%2F{1}%3A1935%2Fwebrtc&stream={2}_aac&cid={3,number,#}&pid={4,number,#}";
        return MessageFormat.format(urlTemplate, cdnHost, streamTranscoderAddr, streamId, cid, pid);
    }
}
