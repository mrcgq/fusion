package ctbrec.recorder;


import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.*;
import ctbrec.event.EventBusHolder;
import ctbrec.event.NoSpaceLeftEvent;
import ctbrec.event.RecordingStateChangedEvent;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.dto.RecordingDto;
import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.io.json.mapper.RecordingMapper;
import ctbrec.sites.Site;
import lombok.AllArgsConstructor;
import lombok.Data;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONObject;
import org.mapstruct.factory.Mappers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class RemoteRecorder implements Recorder {

    private static final String SUCCESS = "success";
    private static final String COULDNT_SEND_REQUEST_SERVER_HTTP_STATUS = "Couldn't send request to server. Response {} - {}";

    private static final Logger LOG = LoggerFactory.getLogger(RemoteRecorder.class);

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String LOG_MSG_SENDING_REQUERST = "Sending request to recording server: {}";

    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();

    private List<Model> models = Collections.emptyList();
    private List<Model> onlineModels = Collections.emptyList();
    private List<Recording> recordings = Collections.emptyList();
    private final Set<ModelGroup> modelGroups = new HashSet<>();
    private final ReentrantLock modelGroupLock = new ReentrantLock();
    private final List<Site> sites;
    private long spaceTotal = -1;
    private long spaceFree = -1;
    private boolean noSpaceLeftDetected = false;

    private final Config config;
    private final HttpClient client;
    private Instant lastSync = Instant.EPOCH;
    private final SyncThread syncThread;

    public RemoteRecorder(Config config, HttpClient client, List<Site> sites) {
        this.config = config;
        this.client = client;
        this.sites = sites;

        syncThread = new SyncThread();
        syncThread.start();
    }

    private String getRecordingEndpoint() {
        return config.getServerUrl() + "/rec";
    }

    @Override
    public void addModel(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        LOG.trace("Model marked: {}", model.isMarkedForLaterRecording());
        LOG.trace("Model paused: {}", model.isSuspended());
        LOG.trace("Model until: {}", model.getRecordUntil().equals(Instant.ofEpochMilli(Model.RECORD_INDEFINITELY)) ? "no limit" : model.getRecordUntil());
        LOG.trace("Model after: {}", model.getRecordUntilSubsequentAction());
        sendRequest("start", model);
        findModel(model).ifPresent(cachedModel -> {
            cachedModel.setSuspended(model.isSuspended());
            cachedModel.setForcePriority(model.isForcePriority());
            cachedModel.setMarkedForLaterRecording(model.isMarkedForLaterRecording());
            cachedModel.setRecordUntil(model.getRecordUntil());
            cachedModel.setRecordUntilSubsequentAction(model.getRecordUntilSubsequentAction());
        });
    }

    @Override
    public void stopRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("stop", model);
        models.remove(model);
        onlineModels.remove(model);
    }

    @Override
    public void stopRecordingAt(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("stopAt", model);
    }

    private Optional<String> sendRequest(String action) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        String msg = "{\"action\": \"" + action + "\"}";
        LOG.trace(LOG_MSG_SENDING_REQUERST, msg);
        RequestBody requestBody = RequestBody.Companion.create(msg, JSON);
        Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(requestBody);
        addHmacIfNeeded(msg, builder);
        Request request = builder.build();
        try (Response response = client.execute(request)) {
            String responseBody = response.body().string();
            if (response.isSuccessful()) {
                return Optional.of(responseBody);
            } else {
                LOG.error(COULDNT_SEND_REQUEST_SERVER_HTTP_STATUS, response.code(), responseBody);
                return Optional.empty();
            }
        }
    }

    private void sendRequest(String action, Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        String payload = mapper.writeValueAsString(new ModelRequest(action, model));
        LOG.trace(LOG_MSG_SENDING_REQUERST, payload);
        RequestBody body = RequestBody.Companion.create(payload, JSON);
        Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
        addHmacIfNeeded(payload, builder);
        Request request = builder.build();
        try (Response response = client.execute(request)) {
            String json = response.body().string();
            if (response.isSuccessful()) {
                ModelListResponse resp = mapper.readValue(json, ModelListResponse.class);
                if (!resp.status.equals(SUCCESS)) {
                    throw new IOException("Server returned error " + resp.status + " " + resp.msg);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void sendRequest(String action, Recording recording, Runnable... onSuccess) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        RecordingRequest recReq = new RecordingRequest(action, recording);
        String msg = mapper.writeValueAsString(recReq);
        RequestBody body = RequestBody.Companion.create(msg, JSON);
        Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
        LOG.trace(LOG_MSG_SENDING_REQUERST, msg);
        addHmacIfNeeded(msg, builder);
        Request request = builder.build();
        try (Response response = client.execute(request)) {
            String json = response.body().string();
            RecordingListResponse resp = mapper.readValue(json, RecordingListResponse.class);
            if (response.isSuccessful()) {
                if (!resp.status.equals(SUCCESS)) {
                    throw new IOException("Request failed: " + resp.msg);
                } else {
                    for (Runnable callback : onSuccess) {
                        callback.run();
                    }
                }
            } else {
                throw new IOException("Request failed: " + resp.msg);
            }
        }
    }

    private void sendRequest(String action, ModelGroup modelGroup) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        String payload = mapper.writeValueAsString(new ModelGroupRequest(action, modelGroup));
        LOG.trace(LOG_MSG_SENDING_REQUERST, payload);
        RequestBody body = RequestBody.Companion.create(payload, JSON);
        Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
        addHmacIfNeeded(payload, builder);
        Request request = builder.build();
        try (Response response = client.execute(request)) {
            if (response.isSuccessful()) {
                String json = response.body().string();
                updateModelGroups(json);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void updateModelGroups(String responseBody) throws IOException {
        ModelGroupListResponse resp = mapper.readValue(responseBody, ModelGroupListResponse.class);
        if (!resp.status.equals(SUCCESS)) {
            throw new IOException("Server returned error " + resp.status + " " + resp.msg);
        }

        modelGroupLock.lock();
        try {
            modelGroups.clear();
            modelGroups.addAll(resp.groups);
        } finally {
            modelGroupLock.unlock();
        }
    }

    private void addHmacIfNeeded(String msg, Builder builder) throws InvalidKeyException, NoSuchAlgorithmException {
        if (Config.getInstance().getSettings().requireAuthentication) {
            byte[] key = Config.getInstance().getSettings().key;
            String hmac = Hmac.calculate(msg, key);
            builder.addHeader("CTBREC-HMAC", hmac);
        }
    }

    @Override
    public boolean isTracked(Model model) {
        Optional<Model> m = findModel(model);
        boolean markedForRecording = m.map(Model::isMarkedForLaterRecording).orElse(false);
        return m.isPresent() && !markedForRecording;
    }

    @Override
    public boolean isSuspended(Model model) {
        return findModel(model).map(Model::isSuspended).orElse(false);
    }

    @Override
    public boolean isForcePriority(Model model) {
        return findModel(model).map(Model::isForcePriority).orElse(false);
    }

    @Override
    public boolean isMarkedForLaterRecording(Model model) {
        return findModel(model).map(Model::isMarkedForLaterRecording).orElse(false);
    }

    private Optional<Model> findModel(Model m) {
        int index = Optional.ofNullable(models).map(list -> list.indexOf(m)).orElse(-1);
        if (index >= 0) {
            return Optional.ofNullable(models).map(mdls -> mdls.get(index));
        } else {
            return Optional.empty();
        }
    }

    @Override
    public List<Model> getModels() {
        if (!lastSync.equals(Instant.EPOCH) && lastSync.isBefore(Instant.now().minusSeconds(60))) {
            throw new IllegalStateException("Last sync was over a minute ago");
        }
        return new ArrayList<>(models);
    }

    @Override
    public void shutdown(boolean immediately) {
        syncThread.stopThread();
    }

    private class SyncThread extends Thread {
        private static final String SERVER_RETURNED_ERROR = "Server returned error: {} - {}";
        private static final String COULDNT_SYNCHRONIZE_WITH_SERVER = "Couldn't synchronize with server";
        private static final String COULDNT_SYNCHRONIZE_WITH_SERVER_HTTP_STATUS = "Couldn't synchronize with server. HTTP status: {} - {}";
        private volatile boolean running = false;
        private static final long ONE_HUNDRED_MIB = 100 * 1024 * 1024L;

        public SyncThread() {
            setName("RemoteRecorder SyncThread");
            setDaemon(true);
        }

        @Override
        public void run() {
            running = true;
            while (running) {
                syncModels();
                syncOnlineModels();
                syncSpace();
                syncRecordings();
                syncModelGroups();
                sleep();
            }
        }

        private void syncModelGroups() {
            try {
                sendRequest("listModelGroups").ifPresent(body -> {
                    try {
                        updateModelGroups(body);
                    } catch (IOException e) {
                        LOG.error("Error while loading model groups from server", e);
                    }
                });
            } catch (IOException | InvalidKeyException | NoSuchAlgorithmException e) {
                LOG.error("Error while loading model groups from server", e);
            }
        }

        private void syncSpace() {
            try {
                String msg = "{\"action\": \"space\"}";
                RequestBody body = RequestBody.Companion.create(msg, JSON);
                Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
                addHmacIfNeeded(msg, builder);
                Request request = builder.build();
                try (Response response = client.execute(request)) {
                    String json = response.body().string();
                    if (response.isSuccessful()) {
                        JSONObject resp = new JSONObject(json);
                        spaceTotal = resp.getLong("spaceTotal");
                        spaceFree = resp.getLong("spaceFree");
                        long throughput = resp.getLong("throughput");
                        Duration timeframe = Duration.ofMillis(resp.getInt("throughputTimeframe"));
                        BandwidthMeter.setThroughput(throughput, timeframe);
                        long minimumSpaceLeftInBytes = resp.optLong("minimumSpaceLeftInBytes");
                        if (minimumSpaceLeftInBytes > 0 && minimumSpaceLeftInBytes >= spaceFree
                                || minimumSpaceLeftInBytes == 0 && spaceFree < ONE_HUNDRED_MIB) {
                            if (!noSpaceLeftDetected) {
                                EventBusHolder.BUS.post(new NoSpaceLeftEvent());
                            }
                            noSpaceLeftDetected = true;
                        } else {
                            noSpaceLeftDetected = false;
                        }
                    } else {
                        LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER_HTTP_STATUS, response.code(), json);
                    }
                }
            } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
                LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER, e);
            }
        }

        private void syncModels() {
            try {
                String msg = "{\"action\": \"list\"}";
                RequestBody body = RequestBody.Companion.create(msg, JSON);
                Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
                addHmacIfNeeded(msg, builder);
                Request request = builder.build();
                try (Response response = client.execute(request)) {
                    String json = response.body().string();
                    if (response.isSuccessful()) {
                        ModelListResponse resp = mapper.readValue(json, ModelListResponse.class);
                        if (resp.status.equals(SUCCESS)) {
                            models = resp.models.stream().map(Mappers.getMapper(ModelMapper.class)::toModel).collect(Collectors.toList());
                            for (Model model : models) {
                                for (Site site : sites) {
                                    if (site.isSiteForModel(model)) {
                                        model.setSite(site);
                                    }
                                }
                            }
                            lastSync = Instant.now();
                        } else {
                            LOG.error(SERVER_RETURNED_ERROR, resp.status, resp.msg);
                        }
                    } else {
                        LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER_HTTP_STATUS, response.code(), json);
                    }
                }
            } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
                LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER, e);
            }
        }

        private void syncOnlineModels() {
            try {
                String msg = "{\"action\": \"listOnline\"}";
                RequestBody body = RequestBody.Companion.create(msg, JSON);
                Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
                addHmacIfNeeded(msg, builder);
                Request request = builder.build();
                try (Response response = client.execute(request)) {
                    String json = response.body().string();
                    if (response.isSuccessful()) {
                        ModelListResponse resp = mapper.readValue(json, ModelListResponse.class);
                        if (resp.status.equals(SUCCESS)) {
                            onlineModels = resp.models.stream().map(Mappers.getMapper(ModelMapper.class)::toModel).collect(Collectors.toList());
                            for (Model model : models) {
                                for (Site site : sites) {
                                    if (site.isSiteForModel(model)) {
                                        model.setSite(site);
                                    }
                                }
                            }
                        } else {
                            LOG.error(SERVER_RETURNED_ERROR, resp.status, resp.msg);
                        }
                    } else {
                        LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER_HTTP_STATUS, response.code(), json);
                    }
                }
            } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
                LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER, e);
            }
        }

        private void syncRecordings() {
            try {
                String msg = "{\"action\": \"recordings\"}";
                RequestBody body = RequestBody.Companion.create(msg, JSON);
                Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
                addHmacIfNeeded(msg, builder);
                Request request = builder.build();
                try (Response response = client.execute(request)) {
                    String json = response.body().string();
                    if (response.isSuccessful()) {
                        RecordingListResponse resp = mapper.readValue(json, RecordingListResponse.class);
                        if (resp.status.equals(SUCCESS)) {
                            List<Recording> newRecordings = resp.recordings.stream()
                                    .map(dto -> {
                                        var rec = Mappers.getMapper(RecordingMapper.class).toRecording(dto);
                                        rec.setSizeInByte(dto.getSizeInByte());
                                        rec.setLastSizeUpdate(Instant.now().toEpochMilli());
                                        return rec;
                                    })
                                    .collect(Collectors.toList());
                            // fire changed events
                            for (Recording recording : recordings) {
                                if (newRecordings.contains(recording)) {
                                    int idx = newRecordings.indexOf(recording);
                                    Recording newRecording = newRecordings.get(idx);
                                    if (newRecording.getStatus() != recording.getStatus()) {
                                        File file = recording.getAbsoluteFile();
                                        RecordingStateChangedEvent evt = new RecordingStateChangedEvent(file, newRecording.getStatus(), recording.getModel(),
                                                recording.getStartDate());
                                        EventBusHolder.BUS.post(evt);
                                    }
                                }
                            }
                            // fire recording started event
                            List<Recording> justStarted = new ArrayList<>(newRecordings);
                            justStarted.removeAll(recordings);
                            for (Recording recording : justStarted) {
                                if (recording.getStatus() == Recording.State.RECORDING) {
                                    File file = recording.getAbsoluteFile();
                                    RecordingStateChangedEvent evt = new RecordingStateChangedEvent(file, recording.getStatus(), recording.getModel(),
                                            recording.getStartDate());
                                    EventBusHolder.BUS.post(evt);
                                }
                            }

                            recordings = newRecordings;

                            // assign a site to the model
                            for (Site site : sites) {
                                for (Recording recording : recordings) {
                                    Model m = recording.getModel();
                                    if (m.getSite() == null && site.isSiteForModel(m)) {
                                        m.setSite(site);
                                    }
                                }
                            }
                        } else {
                            LOG.error(SERVER_RETURNED_ERROR, resp.status, resp.msg);
                        }
                    } else {
                        LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER_HTTP_STATUS, response.code(), json);
                    }
                }
            } catch (IOException | InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
                LOG.error(COULDNT_SYNCHRONIZE_WITH_SERVER, e);
            }
        }

        private void sleep() {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                // interrupted, probably by stopThread
            }
        }

        public void stopThread() {
            running = false;
            interrupt();
        }
    }

    @Data
    private static class ModelListResponse {
        String status;
        String msg;
        List<ModelDto> models;
    }

    @Data
    private static class ModelGroupListResponse {
        String status;
        String msg;
        List<ModelGroup> groups;
    }

    @Data
    private static class SimpleResponse {
        String status;
        String msg;
    }

    @Data
    private static class RecordingListResponse {
        String status;
        String msg;
        List<RecordingDto> recordings;
    }

    @Override
    public List<Recording> getRecordings() {
        return recordings;
    }

    @Override
    public void delete(Recording recording) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("delete", recording, () -> recordings.remove(recording));
    }

    @Data
    public static class ModelRequest {
        private String action;
        private ModelDto model;

        public ModelRequest(String action, Model model) {
            super();
            this.action = action;
            this.model = Mappers.getMapper(ModelMapper.class).toDto(model);
        }
    }

    @Data
    @AllArgsConstructor
    public static class ModelGroupRequest {
        private final String action;
        private final ModelGroup modelGroup;
    }

    @Data
    public static class RecordingRequest {
        private String action;
        private RecordingDto recording;

        public RecordingRequest(String action, Recording recording) {
            super();
            this.action = action;
            this.recording = Mappers.getMapper(RecordingMapper.class).toDto(recording);
        }
    }

    @Override
    public void switchStreamSource(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("switch", model);
    }

    @Override
    public void suspendRecording(Model model) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("suspend", model);
        model.setSuspended(true);
        // update cached model
        int index = models.indexOf(model);
        if (index >= 0) {
            Model m = models.get(index);
            m.setSuspended(true);
        }
    }

    @Override
    public void resumeRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("resume", model);
        model.setSuspended(false);
        // update cached model
        int index = models.indexOf(model);
        if (index >= 0) {
            Model m = models.get(index);
            m.setSuspended(false);
        }
    }

    @Override
    public void forcePriorityRecording(Model model) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("forcePriority", model);
        model.setForcePriority(true);
        // update cached model
        int index = models.indexOf(model);
        if (index >= 0) {
            Model m = models.get(index);
            m.setForcePriority(true);
        }
    }

    @Override
    public void resumePriorityRecording(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("resumePriority", model);
        model.setForcePriority(false);
        // update cached model
        int index = models.indexOf(model);
        if (index >= 0) {
            Model m = models.get(index);
            m.setForcePriority(false);
        }
    }

    @Override
    public List<Model> getOnlineModels() {
        return onlineModels;
    }

    @Override
    public HttpClient getHttpClient() {
        return client;
    }

    @Override
    public long getTotalSpaceBytes() {
        return spaceTotal;
    }

    @Override
    public long getFreeSpaceBytes() {
        return spaceFree;
    }

    @Override
    public void pin(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("pin", recording);
    }

    @Override
    public void unpin(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("unpin", recording);
    }

    @Override
    public void rerunPostProcessing(Recording recording) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        RecordingRequest recReq = new RecordingRequest("rerunPostProcessing", recording);
        String msg = mapper.writeValueAsString(recReq);
        LOG.debug(msg);
        RequestBody body = RequestBody.Companion.create(msg, JSON);
        Request.Builder builder = new Request.Builder().url(getRecordingEndpoint()).post(body);
        addHmacIfNeeded(msg, builder);
        Request request = builder.build();
        try (Response response = client.execute(request)) {
            String json = response.body().string();
            SimpleResponse resp = mapper.readValue(json, SimpleResponse.class);
            if (response.isSuccessful()) {
                if (!resp.status.equals(SUCCESS)) {
                    throw new IOException("Couldn't start post-processing for recording: " + resp.msg);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public void priorityChanged(Model model) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("changePriority", model);
    }

    @Override
    public void setNote(Recording recording, String note) throws IOException, InvalidKeyException, NoSuchAlgorithmException {
        sendRequest("setNote", recording);
    }

    @Override
    public void pause() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("pauseRecorder");
    }

    @Override
    public void resume() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("resumeRecorder");
    }

    @Override
    public int getModelCount() {
        return (int) models.stream().filter(m -> !m.isMarkedForLaterRecording()).count();
    }

    @Override
    public int getGroupedModelCount() {
        Map<String, String> urlToGroup = new HashMap<>();
        for (var group : getModelGroups()) {
            for (var url : group.getModelUrls()) {
                urlToGroup.putIfAbsent(url, group.getName());
            }
        }
        return (int) models.stream().filter(m -> !m.isMarkedForLaterRecording()).map(m -> urlToGroup.getOrDefault(m.getUrl(), m.getName())).distinct().count();
    }

    @Override
    public Set<ModelGroup> getModelGroups() {
        modelGroupLock.lock();
        try {
            return new HashSet<>(modelGroups);
        } finally {
            modelGroupLock.unlock();
        }
    }

    @Override
    public void saveModelGroup(ModelGroup group) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("saveModelGroup", group);
    }

    @Override
    public void deleteModelGroup(ModelGroup group) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        sendRequest("deleteModelGroup", group);
    }

    @Override
    public void markForLaterRecording(Model model, boolean mark) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        model.setMarkedForLaterRecording(mark);
        LOG.info("Marking model {} {}", model, mark);
        if (mark) {
            sendRequest("markForLater", model);
        } else {
            sendRequest("unmarkForLater", model);
        }
        // update locally cached model
        int index = models.indexOf(model);
        if (index >= 0) {
            Model m = models.get(index);
            if (m.isMarkedForLaterRecording() && !mark) {
                models.remove(m);
            }
            m.setMarkedForLaterRecording(mark);
        } else {
            models.add(model);
        }
    }
}
