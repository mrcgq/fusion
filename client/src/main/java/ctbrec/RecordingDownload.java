package ctbrec;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;

import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.recorder.ProgressListener;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;
import ctbrec.recorder.download.hls.SegmentPlaylist;
import ctbrec.recorder.download.hls.SegmentPlaylist.Segment;
import okhttp3.Request;
import okhttp3.Response;

public class RecordingDownload extends MergedFfmpegHlsDownload {

    public RecordingDownload(HttpClient client) {
        super(client);
    }

    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        this.config = config;
        this.model = model;
        this.startTime = startTime;
        this.downloadExecutor = executorService;
        splittingStrategy = initSplittingStrategy(config.getSettings());
    }

    public void downloadFinishedRecording(String segmentPlaylistUri, File target, ProgressListener progressListener, long sizeInBytes)
            throws InvalidKeyException, NoSuchAlgorithmException, IllegalStateException, InterruptedException, IOException, ParseException, PlaylistException  {
        running = true;
        if (Config.getInstance().getSettings().requireAuthentication) {
            URL u = new URL(segmentPlaylistUri);
            String path = u.getPath();
            byte[] key = Config.getInstance().getSettings().key;
            if (!Config.getInstance().getContextPath().isEmpty()) {
                path = path.substring(Config.getInstance().getContextPath().length());
            }
            String hmac = Hmac.calculate(path, key);
            segmentPlaylistUri = segmentPlaylistUri + "?hmac=" + hmac;
        }

        startFfmpegProcess(target);
        for (int i = 0; i < 10 && ffmpegStdIn == null; i++) {
            Thread.sleep(100);
        }

        SegmentPlaylist segmentPlaylist = getNextSegments(segmentPlaylistUri);
        long loadedBytes = 0;
        for (Segment segment : segmentPlaylist.segments) {
            loadedBytes += downloadFile(segment.url, loadedBytes, sizeInBytes, progressListener);
            int progress = (int) (loadedBytes / (double) sizeInBytes * 100);
            progressListener.update(progress);
        }

        internalStop();
    }

    private long downloadFile(String fileUri, long loadedBytes, long totalBytes, ProgressListener progressListener) throws IOException {
        long fileLoadedBytes = 0;
        Request request = new Request.Builder().url(fileUri).addHeader("connection", "keep-alive").build();
        try (Response response = client.execute(request)) {
            if (response.isSuccessful()) {
                InputStream in = response.body().byteStream();
                byte[] b = new byte[1024 * 100];
                int length = -1;
                while ((length = in.read(b)) >= 0) {
                    ffmpegStdIn.write(b, 0, length);
                    fileLoadedBytes += length;
                    int progress = (int) ((loadedBytes + fileLoadedBytes) / (double) totalBytes * 100);
                    progressListener.update(progress);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
        return fileLoadedBytes;
    }
}
