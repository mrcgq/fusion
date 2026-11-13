package ctbrec.recorder.download.hls;

import ctbrec.Model;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.hls.SegmentPlaylist.Segment;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.datatype.jsr310.deser.DurationDeserializer;

import javax.crypto.NoSuchPaddingException;
import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URI;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.time.Instant;
import java.time.Duration;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.recorder.download.hls.AbstractHlsDownload.addHeaders;

public class SegmentDownload implements Callable<SegmentDownload> {
    private static final Logger LOG = LoggerFactory.getLogger(SegmentDownload.class);

    protected final URL url;
    protected final HttpClient client;
    protected final SegmentPlaylist playlist;
    protected final Segment segment;
    protected final Model model;
    protected final OutputStream out;
    protected final Instant createdAt;

    private long size = 0;
    protected boolean failed = false;

    protected Exception exception;

    public SegmentDownload(Model model, SegmentPlaylist playlist, Segment segment, HttpClient client, OutputStream out) throws MalformedURLException {
        this.model = model;
        this.playlist = playlist;
        this.segment = segment;
        this.client = client;
        this.out = out;
        this.url = URI.create(segment.url).toURL();
        this.createdAt = Instant.now();
    }

    @Override
    public SegmentDownload call() {
        var expiresAt = createdAt.plusSeconds(10);
        
        for (int tries = 1; tries <= 3 && !Thread.currentThread().isInterrupted(); tries++) { // NOSONAR
            if (expiresAt.isBefore(Instant.now())) {
                // segment has sexpired, skip it
                LOG.warn("Segment for model {} is late {} seconds", model, Duration.between(expiresAt, Instant.now()));
                failed = true;
                exception = new Exception("Segment expired");
                break;
            }
            
            Request request = createRequest();
            try (Response response = client.execute(request)) {
                handleResponse(response);
                break;
            } catch (FileNotFoundException e) {
                LOG.debug("Segment does not exist {}", url.getFile());
                failed = true;
                exception = e;
                break;
            } catch (InterruptedIOException e) {
                failed = true;
                exception = e;
                break;
            } catch (Exception e) {
                if (tries == 3) {
                    LOG.warn("Error while downloading segment for {}. Segment {} finally failed: {}", model, url, e.getMessage());
                    failed = true;
                    exception = e;
                } else {
                    LOG.debug("Error while downloading segment {} for {} on try {} - {}", url, model, tries, e.getMessage());
                }
            }
        }
        return this;
    }

    protected void handleResponse(Response response) throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException, IOException {
        if (response.isSuccessful()) {
            InputStream in = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).byteStream();
            if (playlist.encrypted) {
                in = new Crypto(playlist.encryptionKeyUrl, client).wrap(in);
            }
            byte[] b = new byte[1024 * 100];
            int length;
            while ((length = in.read(b)) >= 0 && !Thread.currentThread().isInterrupted()) {
                out.write(b, 0, length);
                size += length;
                BandwidthMeter.add(length);
            }
            out.flush();
        } else {
            throw new HttpException(response.code(), response.message());
        }
    }

    protected Request createRequest() {
        Builder builder = new Request.Builder().url(url);
        addHeaders(builder, Optional.ofNullable(model).map(Model::getHttpHeaderFactory).map(HttpHeaderFactory::createSegmentHeaders).orElse(new HashMap<>()), model);
        return builder.build();
    }

    public OutputStream getOutputStream() {
        return out;
    }

    public Segment getSegment() {
        return segment;
    }

    public boolean isFailed() {
        return failed;
    }

    public Exception getException() {
        return exception;
    }

    public long size() {
        return size;
    }
}
