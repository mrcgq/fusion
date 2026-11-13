package ctbrec.notes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.RemoteService;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URLEncoder;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class RemoteModelNotesService extends RemoteService implements ModelNotesService {

    private final HttpClient httpClient;
    private final Config config;

    private Map<String, String> notesCache = Collections.emptyMap();
    private Instant lastUpdate = Instant.EPOCH;

    private final LoadingCache<String, String> cache = CacheBuilder.newBuilder()
            .expireAfterWrite(3, TimeUnit.SECONDS)
            .maximumSize(10000)
            .build(CacheLoader.from(this::updateCache));

    public RemoteModelNotesService(HttpClient httpClient, Config config) {
        this.httpClient = httpClient;
        this.config = config;
        transferOldNotesToServer(config.getSettings().modelNotes);
    }

    private void transferOldNotesToServer(Map<String, String> modelNotes) {
        LocalModelNotesService localModelNotesStore = new LocalModelNotesService(config);
        GlobalThreadPool.submit(() -> {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            List<String> successfullyTransfered = new ArrayList<>();
            for (Map.Entry<String, String> entry : modelNotes.entrySet()) {
                try {
                    log.info("Uploading model notes to server {} - {}", entry.getKey(), entry.getValue());
                    RemoteModelNotesService.this.writeModelNotes(entry.getKey(), entry.getValue());
                    successfullyTransfered.add(entry.getKey());
                } catch (Exception e) {
                    log.warn("Could not transfer model notes from local to remote store: {} {} - {}", entry.getKey(), entry.getValue(), e.getLocalizedMessage());
                }
            }
            for (String s : successfullyTransfered) {
                localModelNotesStore.removeModelNotes(s);
            }
        });
    }

    private synchronized String updateCache(String modelUrl) {
        if (lastUpdate.isBefore(Instant.now().minusSeconds(3))) {
            try {
                notesCache = loadAllModelNotes();
                lastUpdate = Instant.now();
                for (Map.Entry<String, String> entry : notesCache.entrySet()) {
                    cache.put(entry.getKey(), entry.getValue());
                }
            } catch (Exception e) {
                var exception = new CacheLoader.InvalidCacheLoadException("Loading of model notes from server failed");
                exception.initCause(e);
                throw exception;
            }
        }
        return Optional.ofNullable(notesCache.get(modelUrl)).orElse("");
    }

    @Override
    public Map<String, String> loadAllModelNotes() throws IOException {
        Request.Builder builder = new Request.Builder().url(config.getServerUrl() + "/models/notes");
        try {
            addHmacIfNeeded(new byte[0], builder);
            log.trace("Loading all model notes from server");
            try (Response resp = httpClient.execute(builder.build())) {
                if (resp.isSuccessful()) {
                    String body = resp.body().string();
                    log.trace("Model notes from server:\n{}", body);
                    Map<String, String> result = new HashMap<>();
                    JSONObject json = new JSONObject(body);
                    if (json.names() != null) {
                        JSONArray names = json.names();
                        for (int i = 0; i < names.length(); i++) {
                            String name = names.getString(i);
                            result.put(name, json.getString(name));
                        }
                        return Collections.unmodifiableMap(result);
                    } else {
                        return Collections.emptyMap();
                    }
                } else {
                    throw new HttpException(resp.code(), resp.message());
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not load model notes from server", e);
        }
    }

    @Override
    public Optional<String> loadModelNotes(String modelUrl) throws IOException {
        try {
            log.trace("Loading model notes for {}", modelUrl);
            return Optional.ofNullable(cache.get(modelUrl));
        } catch (ExecutionException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void writeModelNotes(String modelUrl, String notes) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(config.getServerUrl() + "/models/notes/" + URLEncoder.encode(modelUrl, UTF_8))
                .post(RequestBody.create(notes, MediaType.parse("text/plain")));
        try {
            addHmacIfNeeded(notes, builder);
            try (Response resp = httpClient.execute(builder.build())) {
                if (resp.isSuccessful()) {
                    cache.put(modelUrl, notes);
                } else {
                    throw new HttpException(resp.code(), resp.message());
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not write model notes to server", e);
        }
    }

    @Override
    public void removeModelNotes(String modelUrl) throws IOException {
        Request.Builder builder = new Request.Builder()
                .url(config.getServerUrl() + "/models/notes/" + URLEncoder.encode(modelUrl, UTF_8))
                .delete();
        try {
            addHmacIfNeeded(new byte[0], builder);
            try (Response resp = httpClient.execute(builder.build())) {
                if (resp.isSuccessful()) {
                    cache.invalidate(modelUrl);
                } else {
                    throw new HttpException(resp.code(), resp.message());
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Could not delete model notes from server", e);
        }
    }
}
