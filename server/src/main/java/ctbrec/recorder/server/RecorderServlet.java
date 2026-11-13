package ctbrec.recorder.server;


import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.*;
import ctbrec.io.BandwidthMeter;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.mapper.ModelMapper;
import ctbrec.io.json.mapper.RecordingMapper;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.server.io.json.dto.RequestDto;
import ctbrec.recorder.server.io.json.mapper.RequestMapper;
import ctbrec.sites.Site;
import ctbrec.sites.SiteUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.mapstruct.factory.Mappers;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import static javax.servlet.http.HttpServletResponse.*;

@Slf4j
public class RecorderServlet extends AbstractCtbrecServlet {

    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();
    private final ModelMapper modelMapper = Mappers.getMapper(ModelMapper.class);
    private final RecordingMapper recordingMapper = Mappers.getMapper(RecordingMapper.class);

    private final Recorder recorder;
    private final List<Site> sites;


    public RecorderServlet(Recorder recorder, List<Site> sites) {
        this.recorder = recorder;
        this.sites = sites;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(SC_OK);
        resp.setContentType("application/json");

        String json = null;
        try {
            PrintWriter responseWriter = resp.getWriter();
            json = body(req);
            boolean isRequestAuthenticated = checkAuthentication(req, json);
            if (!isRequestAuthenticated) {
                sendError(resp, SC_UNAUTHORIZED, "{\"status\": \"error\", \"msg\": \"HMAC does not match\"}");
                return;
            }

            log.debug("Request: {}", json);
            RequestDto requestDto = mapper.readValue(json, RequestDto.class);
            ModelDto modelDto = requestDto.getModel();
            if (requestDto.getAction() == null) {
                sendError(resp, SC_BAD_REQUEST, "{\"status\": \"error\", \"msg\": \"action is missing\"}");
                return;
            }
            if (requestDto.getAction().startsWith("startBy")) {
                log.debug("Starting recording for model {}", modelDto.getUrl());
                if (requestDto.getAction().equals("startByUrl")) {
                    startByUrl(requestDto);
                } else {
                    startByName(requestDto);
                }
                var response = "{\"status\": \"success\", \"msg\": \"Recording started\"}";
                responseWriter.write(response);
                return;
            }

            Request request = Mappers.getMapper(RequestMapper.class).toRequest(mapper.readValue(json, RequestDto.class));
            Model model = request.getModel();
            Optional.ofNullable(model).ifPresent(m -> SiteUtil.getSiteForModel(sites, m).ifPresent(m::setSite));
            Recording recording = request.getRecording();
            switch (requestDto.getAction()) {
                case "start":
                    log.debug("Starting recording for model {} - {}", model.getName(), model.getUrl());
                    log.trace("Model marked: {}", model.isMarkedForLaterRecording());
                    log.trace("Model paused: {}", model.isSuspended());
                    log.trace("Model forced: {}", model.isForcePriority());
                    log.trace("Model until: {}", model.getRecordUntil().equals(Instant.ofEpochMilli(Model.RECORD_INDEFINITELY)) ? "no limit" : model.getRecordUntil());
                    log.trace("Model after: {}", model.getRecordUntilSubsequentAction());
                    recorder.addModel(model);
                    String response = "{\"status\": \"success\", \"msg\": \"Recording started\"}";
                    responseWriter.write(response);
                    break;
                case "stop":
                    GlobalThreadPool.submit(() -> {
                        try {
                            recorder.stopRecording(model);
                        } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | IOException e) {
                            log.error("Couldn't stop recording for model {}", model, e);
                        }
                    });
                    response = "{\"status\": \"success\", \"msg\": \"Stopping recording\"}";
                    responseWriter.write(response);
                    break;
                case "stopAt":
                    GlobalThreadPool.submit(() -> {
                        try {
                            recorder.stopRecordingAt(model);
                        } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | IOException e) {
                            log.error("Couldn't stop recording for model {}", model, e);
                        }
                    });
                    response = "{\"status\": \"success\", \"msg\": \"Stopping recording\"}";
                    responseWriter.write(response);
                    break;
                case "list":
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"List of models\", \"models\": [");
                    for (Iterator<Model> iterator = recorder.getModels().iterator(); iterator.hasNext(); ) {
                        Model m = iterator.next();
                        responseWriter.write(mapper.writeValueAsString(modelMapper.toDto(m)));
                        if (iterator.hasNext()) {
                            responseWriter.write(',');
                        }
                    }
                    responseWriter.write("]}");
                    break;
                case "listOnline":
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"List of online models\", \"models\": [");
                    for (Iterator<Model> iterator = recorder.getOnlineModels().iterator(); iterator.hasNext(); ) {
                        Model m = iterator.next();
                        responseWriter.write(mapper.writeValueAsString(modelMapper.toDto(m)));
                        if (iterator.hasNext()) {
                            responseWriter.write(',');
                        }
                    }
                    responseWriter.write("]}");
                    break;
                case "recordings":
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"List of recordings\", \"recordings\": [");
                    List<Recording> recordings = recorder.getRecordings();
                    for (Iterator<Recording> iterator = recordings.iterator(); iterator.hasNext(); ) {
                        Recording rec = iterator.next();
                        String recJSON = mapper.writeValueAsString(recordingMapper.toDto(rec));
                        log.debug("Rec: {}", recJSON);
                        responseWriter.write(recJSON);
                        if (iterator.hasNext()) {
                            responseWriter.write(',');
                        }
                    }
                    responseWriter.write("]}");
                    break;
                case "delete":
                    recorder.delete(recording);
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"List of recordings\", \"recordings\": [");
                    responseWriter.write(mapper.writeValueAsString(recordingMapper.toDto(recording)));
                    responseWriter.write("]}");
                    break;
                case "pin":
                    recorder.pin(recording);
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"List of recordings\", \"recordings\": [");
                    responseWriter.write(mapper.writeValueAsString(recordingMapper.toDto(recording)));
                    responseWriter.write("]}");
                    break;
                case "unpin":
                    recorder.unpin(recording);
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"Note saved\", \"recordings\": [");
                    responseWriter.write(mapper.writeValueAsString(recordingMapper.toDto(recording)));
                    responseWriter.write("]}");
                    break;
                case "setNote":
                    recorder.setNote(recording, recording.getNote());
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"List of recordings\", \"recordings\": [");
                    responseWriter.write(mapper.writeValueAsString(recordingMapper.toDto(recording)));
                    responseWriter.write("]}");
                    break;
                case "rerunPostProcessing":
                    recorder.rerunPostProcessing(recording);
                    responseWriter.write("{\"status\": \"success\", \"msg\": \"Post-Processing triggered\"}");
                    break;
                case "switch":
                    recorder.switchStreamSource(model);
                    response = "{\"status\": \"success\", \"msg\": \"Resolution switched\"}";
                    responseWriter.write(response);
                    break;
                case "suspend":
                    log.debug("Suspend recording for model {} - {}", model.getName(), model.getUrl());
                    GlobalThreadPool.submit(() -> {
                        try {
                            recorder.suspendRecording(model);
                        } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | IOException e) {
                            log.error("Couldn't suspend recording for model {}", model, e);
                        }
                    });
                    response = "{\"status\": \"success\", \"msg\": \"Suspending recording\"}";
                    responseWriter.write(response);
                    break;
                case "resume":
                    log.debug("Resume recording for model {} - {}", model.getName(), model.getUrl());
                    recorder.resumeRecording(model);
                    response = "{\"status\": \"success\", \"msg\": \"Recording resumed\"}";
                    responseWriter.write(response);
                    break;
                case "space":
                    JSONObject jsonResponse = new JSONObject();
                    jsonResponse.put("status", "success");
                    jsonResponse.put("spaceTotal", recorder.getTotalSpaceBytes());
                    jsonResponse.put("spaceFree", recorder.getFreeSpaceBytes());
                    jsonResponse.put("throughput", BandwidthMeter.getThroughput());
                    jsonResponse.put("throughputTimeframe", BandwidthMeter.getTimeframe().toMillis());
                    jsonResponse.put("minimumSpaceLeftInBytes", Config.getInstance().getSettings().minimumSpaceLeftInBytes);
                    responseWriter.write(jsonResponse.toString());
                    break;
                case "changePriority":
                    recorder.priorityChanged(model);
                    response = "{\"status\": \"success\"}";
                    responseWriter.write(response);
                    break;
                case "pauseRecorder":
                    recorder.pause();
                    response = "{\"status\": \"success\"}";
                    responseWriter.write(response);
                    break;
                case "resumeRecorder":
                    recorder.resume();
                    response = "{\"status\": \"success\"}";
                    responseWriter.write(response);
                    break;
                case "forcePriority":
                    log.debug("Force ignore priority for model {} - {}", model.getName(), model.getUrl());
                    recorder.forcePriorityRecording(model);
                    response = "{\"status\": \"success\", \"msg\": \"Forcing ignore priority\"}";
                    responseWriter.write(response);
                    break;
                case "resumePriority":
                    log.debug("Resume respecting priority for model {} - {}", model.getName(), model.getUrl());
                    GlobalThreadPool.submit(() -> {
                        try {
                            recorder.resumePriorityRecording(model);
                        } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException | IOException e) {
                            log.error("Couldn't resume respecting priority for model {}", model, e);
                        }
                    });
                    response = "{\"status\": \"success\", \"msg\": \"Resuming respecting priority\"}";
                    responseWriter.write(response);
                    break;
                case "saveModelGroup":
                    recorder.saveModelGroup(request.getModelGroup());
                    sendModelGroups(resp, recorder.getModelGroups());
                    break;
                case "deleteModelGroup":
                    recorder.deleteModelGroup(request.getModelGroup());
                    sendModelGroups(resp, recorder.getModelGroups());
                    break;
                case "listModelGroups":
                    sendModelGroups(resp, recorder.getModelGroups());
                    break;
                case "markForLater":
                    log.debug("Mark model {} for later recording", model.getName());
                    response = "{\"status\": \"success\", \"msg\": \"Model marked for later recording\"}";
                    responseWriter.write(response);
                    recorder.markForLaterRecording(model, true);
                    break;
                case "unmarkForLater":
                    log.debug("Unmark model {} for later recording", model.getName());
                    response = "{\"status\": \"success\", \"msg\": \"Model has been unmarked\"}";
                    responseWriter.write(response);
                    recorder.markForLaterRecording(model, false);
                    break;
                default:
                    sendError(resp, SC_BAD_REQUEST, "{\"status\": \"error\", \"msg\": \"Unknown action [" + request.getAction() + "]\"}");
                    break;
            }
        } catch (Exception e) {
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
            JSONObject response = new JSONObject();
            response.put("status", "error");
            response.put("msg", e.getMessage());
            resp.getWriter().write(response.toString());
            log.error("Unexpected error", e);
            if (json != null) {
                log.debug("Request: {}", json);
            }
        }
    }

    private void sendError(HttpServletResponse resp, int code, String body) throws IOException {
        resp.setStatus(code);
        resp.getWriter().write(body);
    }

    private void sendModelGroups(HttpServletResponse resp, Set<ModelGroup> modelGroups) throws IOException {
        JSONObject jsonResponse = new JSONObject();
        jsonResponse.put("status", "success");
        jsonResponse.put("groups", modelGroups);
        resp.getWriter().write(jsonResponse.toString());
    }

    private void startByUrl(RequestDto request) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        String url = request.getModel().getUrl().toString();
        for (Site site : sites) {
            Model model = site.createModelFromUrl(url);
            if (model != null) {
                recorder.addModel(model);
                return;
            }
        }
        throw new IllegalArgumentException("No site found to record " + url);
    }

    private void startByName(RequestDto request) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        String[] input = request.getModel().getUrl().toString().split(":");
        if (input.length != 2) {
            throw new IllegalArgumentException("Invalid input. Should be site:model");
        }
        String siteName = input[0];
        String modelName = input[1];
        for (Site site : sites) {
            if (Objects.equals(siteName.toLowerCase(), site.getClass().getSimpleName().toLowerCase())) {
                Model m = site.createModel(modelName);
                recorder.addModel(m);
                return;
            }
        }
        throw new IllegalArgumentException("No site found to record " + request.getModel().getUrl());
    }
}
