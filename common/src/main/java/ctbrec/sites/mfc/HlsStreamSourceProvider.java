package ctbrec.sites.mfc;

import com.iheartradio.m3u8.*;
import com.iheartradio.m3u8.data.MasterPlaylist;
import com.iheartradio.m3u8.data.Playlist;
import com.iheartradio.m3u8.data.PlaylistData;
import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.StreamSource;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static ctbrec.io.HttpConstants.*;

public record HlsStreamSourceProvider(HttpClient httpClient) implements StreamSourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(HlsStreamSourceProvider.class);

    @Override
    public List<StreamSource> getStreamSources(String streamUrl) throws IOException, ExecutionException, ParseException, PlaylistException {
        MasterPlaylist masterPlaylist = getMasterPlaylist(streamUrl);
        List<StreamSource> sources = new ArrayList<>();
        for (PlaylistData playlist : masterPlaylist.getPlaylists()) {
            if (playlist.hasStreamInfo()) {
                StreamSource src = new StreamSource();
                src.setBandwidth(playlist.getStreamInfo().getBandwidth());
                if (playlist.getStreamInfo().getResolution() != null) {
                    src.setWidth(playlist.getStreamInfo().getResolution().width);
                    src.setHeight(playlist.getStreamInfo().getResolution().height);
                } else {
                    src.setWidth(StreamSource.UNKNOWN);
                    src.setHeight(StreamSource.UNKNOWN);
                }
                String masterUrl = streamUrl;
                String baseUrl = masterUrl.substring(0, masterUrl.lastIndexOf('/') + 1);
                String segmentUri = baseUrl + playlist.getUri();
                src.setMediaPlaylistUrl(segmentUri);
                LOG.trace("Media playlist {}", src.getMediaPlaylistUrl());
                sources.add(src);
            }
        }
        return sources;
    }

    private MasterPlaylist getMasterPlaylist(String streamUrl) throws IOException, ParseException, PlaylistException {
        if (streamUrl == null) {
            throw new IllegalStateException("Stream url unknown");
        }
        LOG.trace("Loading master playlist {}", streamUrl);
        Request req = new Request.Builder()
                .url(streamUrl)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(CONNECTION, KEEP_ALIVE)
                .header(ORIGIN, MyFreeCams.baseUrl)
                .header(REFERER, MyFreeCams.baseUrl + '/')
                .build();
        try (Response response = httpClient.execute(req)) {
            if (response.isSuccessful()) {
                InputStream inputStream = Objects.requireNonNull(response.body(), "HTTP response is null").byteStream();
                PlaylistParser parser = new PlaylistParser(inputStream, Format.EXT_M3U, Encoding.UTF_8, ParsingMode.LENIENT);
                Playlist playlist = parser.parse();
                MasterPlaylist master = playlist.getMasterPlaylist();
                return master;
            } else {
                throw new HttpException(response.code(), response.message() + " - " + streamUrl);
            }
        }
    }
}
