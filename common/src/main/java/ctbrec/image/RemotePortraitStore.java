package ctbrec.image;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Hmac;
import ctbrec.RemoteService;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ctbrec.io.HttpConstants.MIMETYPE_IMAGE_JPG;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class RemotePortraitStore extends RemoteService implements PortraitStore {

    private final HttpClient httpClient;
    private final Config config;

    public RemotePortraitStore(HttpClient httpClient, Config config) {
        this.httpClient = httpClient;
        this.config = config;
        transferOldPortraitsToServer(config.getSettings().modelPortraits);
    }

    private void transferOldPortraitsToServer(Map<String, String> modelPortraits) {
        LocalPortraitStore localPortraitStore = new LocalPortraitStore(config);
        GlobalThreadPool.submit(() -> {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<String> successfullyTransfered = new ArrayList<>();
            for (Map.Entry<String, String> entry : modelPortraits.entrySet()) {
                localPortraitStore.loadModelPortraitById(entry.getValue()).ifPresent(data -> {
                    try {
                        log.info("Uploading portrait to server {} - {} bytes", entry.getKey(), data.length);
                        RemotePortraitStore.this.writePortrait(entry.getKey(), data);
                        successfullyTransfered.add(entry.getKey());
                    } catch (Exception e) {
                        log.warn("Could not transfer portrait from local to remote store: {} {}", entry.getKey(), entry.getValue());
                    }
                });
            }
            for (String s : successfullyTransfered) {
                try {
                    localPortraitStore.removePortrait(s);
                } catch (IOException e) {
                    log.warn("Could not delete local potrait, which has been transferred to the server: {} ", s, e);
                }
            }
        });
    }

    private String getEndpoint() {
        return config.getServerUrl() + "/image/portrait";
    }

    private String getModelUrlEndpoint() {
        return getEndpoint() + "/url/";
    }

    @Override
    public Optional<byte[]> loadModelPortraitById(String id) throws IOException {
        return load(getEndpoint() + '/' + id);
    }

    @Override
    public Optional<byte[]> loadModelPortraitByModelUrl(String modelUrl) throws IOException {
        return load(getModelUrlEndpoint() + URLEncoder.encode(modelUrl, UTF_8));
    }

    private Optional<byte[]> load(String url) throws IOException {
        Request.Builder builder = new Request.Builder().url(url);
        try {
            addHmacIfNeeded(new byte[0], builder);
            try (Response resp = httpClient.execute(builder.build())) {
                if (resp.isSuccessful()) {
                    return Optional.of(resp.body().bytes());
                } else {
                    throw new HttpException(resp.code(), resp.message());
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not load portrait from server", e);
        }
    }

    @Override
    public void writePortrait(String modelUrl, byte[] data) throws IOException {
        RequestBody body = RequestBody.create(data, MediaType.parse(MIMETYPE_IMAGE_JPG));
        Request.Builder builder = new Request.Builder()
                .url(getModelUrlEndpoint() + URLEncoder.encode(modelUrl, UTF_8))
                .post(body);
        try {
            addHmacIfNeeded(data, builder);
            try (Response resp = httpClient.execute(builder.build())) {
                if (!resp.isSuccessful()) {
                    throw new HttpException(resp.code(), resp.message());
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could upload portrait to server", e);
        }
    }

    @Override
    public void removePortrait(String modelUrl) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(getModelUrlEndpoint() + URLEncoder.encode(modelUrl, UTF_8))
                .delete();
        try {
            addHmacIfNeeded(new byte[0], builder);
            try (Response resp = httpClient.execute(builder.build())) {
                if (!resp.isSuccessful()) {
                    throw new HttpException(resp.code(), resp.message());
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not delete portrait from server", e);
        }
    }
}
