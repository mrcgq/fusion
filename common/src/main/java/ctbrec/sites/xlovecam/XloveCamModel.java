package ctbrec.sites.xlovecam;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import ctbrec.AbstractModel;
import ctbrec.Config;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;

import javax.xml.bind.JAXBException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.Model.State.*;
import static ctbrec.io.HttpConstants.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class XloveCamModel extends AbstractModel {

    private static final Pattern HLS_PLAYLIST_PATTERN = Pattern.compile("\"hlsPlaylist\":\"(.*?)\",");

    private boolean online = false;

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        if (ignoreCache) {
            String body = getModelPage();
            Matcher m = HLS_PLAYLIST_PATTERN.matcher(body);
            online = m.find();
            onlineState = online ? ONLINE : OFFLINE;
        }
        return online;
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
        MasterPlaylist masterPlaylist = getMasterPlaylist();
        List<StreamSource> sources = new ArrayList<>();
        for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
            if (playlist.hasStreamInfo()) {
                StreamSource src = new StreamSource();
                src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                src.setHeight(Optional.ofNullable(playlist.getStreamInfo().getResolution()).map(r -> r.height).orElse(0));
                src.setWidth(Optional.ofNullable(playlist.getStreamInfo().getResolution()).map(r -> r.width).orElse(0));
                src.setMediaPlaylistUrl(playlist.getUri());
                log.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    private MasterPlaylist getMasterPlaylist() throws IOException, ParseException, PlaylistException {
        String modelPage = getModelPage();
        Matcher m = HLS_PLAYLIST_PATTERN.matcher(modelPage);
        if (m.find() && m.groupCount() > 0) {
            String hlsPlaylist = m.group(1);
            Request req = new Request.Builder()
                    .url(hlsPlaylist)
                    .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                    .build();
            try (Response response = getSite().getHttpClient().execute(req)) {
                if (response.isSuccessful()) {
                    String body = response.body().string();
                    log.trace(body);
                    InputStream inputStream = new ByteArrayInputStream(body.getBytes(UTF_8));
                    PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                    Playlist playlist = parser.parse();
                    MasterPlaylist master = playlist.getMasterPlaylist();
                    return master;
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        } else {
            throw new HttpException(404, "HLS playlist not found");
        }
    }

    private String getModelPage() throws IOException {
        String url = XloveCam.mobileUrl + "/en/model/" + getName() + '/';
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                return response.body().string();
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public void invalidateCacheEntries() {
        // nothing to do
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        // not implemented yet
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        return new int[]{0, 0};
    }

    @Override
    public boolean follow() throws IOException {
        return false;
    }

    @Override
    public boolean unfollow() throws IOException {
        return false;
    }

}
