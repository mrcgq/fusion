package ctbrec.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.io.HttpClient;
import ctbrec.recorder.ProgressListener;
import okhttp3.Request;

public class FileDownload {

    private static final Logger LOG = LoggerFactory.getLogger(FileDownload.class);

    private HttpClient httpClient;
    private ProgressListener downloadListener;

    public FileDownload(HttpClient httpClient, ProgressListener downloadListener) {
        this.httpClient = httpClient;
        this.downloadListener = downloadListener;
    }

    public void start(URL url, File target) throws IOException {
        LOG.trace("Downloading file {} to {}", url, target);
        var request = new Request.Builder().url(url).addHeader("connection", "keep-alive").build();
        var response = httpClient.execute(request);
        var fileSize = Long.parseLong(response.header("Content-Length", String.valueOf(Long.MAX_VALUE)));
        InputStream in = null;
        try (var fos = new FileOutputStream(target)) {
            in = response.body().byteStream();
            var b = new byte[1024 * 100];
            long totalBytesRead = 0;
            int length = -1;
            while ((length = in.read(b)) >= 0) {
                fos.write(b, 0, length);
                totalBytesRead += length;
                int progress = (int)(totalBytesRead * 100d / fileSize);
                downloadListener.update(progress);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            response.close();
        }
    }

}
