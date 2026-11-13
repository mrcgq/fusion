package ctbrec.recorder.server;

import static javax.servlet.http.HttpServletResponse.*;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.*;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.Settings;
import ctbrec.Settings.SplitStrategy;
import ctbrec.io.BoundField;

public class ConfigServlet extends AbstractCtbrecServlet {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigServlet.class);
    private Config config;
    private Settings settings;

    public enum DataType {
        STRING, BOOLEAN, INTEGER, LONG, DOUBLE, SPLIT_STRATEGY, TIME, STRING_LIST
    }

    public ConfigServlet(Config config) {
        this.config = config;
        this.settings = config.getSettings();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            boolean authenticated = checkAuthentication(req, body(req));
            if (!authenticated) {
                resp.setStatus(SC_UNAUTHORIZED);
                String response = "HMAC does not match";
                resp.getWriter().write(response);
                return;
            }

            JSONArray json = new JSONArray();
            addParameter("concurrentRecordings", "Concurrent Recordings", DataType.INTEGER, settings.concurrentRecordings, json);
            addParameter("chaturbateMsBetweenRequests", "Chaturbate time between requests (ms)", DataType.INTEGER, settings.chaturbateMsBetweenRequests, json);
            addParameter("defaultPriority", "Default Priority", DataType.INTEGER, settings.defaultPriority, json);
            addParameter("deleteOrphanedRecordingMetadata", "Delete orphaned recording metadata", DataType.BOOLEAN, settings.deleteOrphanedRecordingMetadata, json);
            addParameter("ffmpegFileSuffix", "File Suffix", DataType.STRING, settings.ffmpegFileSuffix, json);
            addParameter("ffmpegMergedDownloadArgs", "FFmpeg Parameters", DataType.STRING, settings.ffmpegMergedDownloadArgs, json);
            addParameter("httpPort", "HTTP port", DataType.INTEGER, settings.httpPort, json);
            addParameter("httpSecurePort", "HTTPS port", DataType.INTEGER, settings.httpSecurePort, json);
            addParameter("httpUserAgent", "User-Agent", DataType.STRING, settings.httpUserAgent, json);
            addParameter("httpUserAgentMobile", "Mobile User-Agent", DataType.STRING, settings.httpUserAgentMobile, json);
            addParameter("minimumResolution", "Minimum Resolution", DataType.INTEGER, settings.minimumResolution, json);
            addParameter("maximumResolution", "Maximum Resolution", DataType.INTEGER, settings.maximumResolution, json);
            addParameter("minimumSpaceLeftInBytes", "Leave Space On Device (bytes)", DataType.LONG, settings.minimumSpaceLeftInBytes, json);
            addParameter("onlineCheckIntervalInSecs", "Online Check Interval (secs)", DataType.INTEGER, settings.onlineCheckIntervalInSecs, json);
            addParameter("onlineCheckSkipsPausedModels", "Skip Online Check For Paused Models", DataType.BOOLEAN, settings.onlineCheckSkipsPausedModels, json);
            addParameter("postProcessingThreads", "Post-Processing Threads", DataType.INTEGER, settings.postProcessingThreads, json);
            addParameter("recordingsDir", "Recordings Directory", DataType.STRING, settings.recordingsDir, json);
            addParameter("recordSingleFile", "Record Single File", DataType.BOOLEAN, settings.recordSingleFile, json);
            addParameter("splitStrategy", "Split Strategy", DataType.SPLIT_STRATEGY, settings.splitStrategy, json);
            addParameter("splitRecordingsAfterSecs", "Split Recordings After (secs)", DataType.INTEGER, settings.splitRecordingsAfterSecs, json);
            addParameter("splitRecordingsBiggerThanBytes", "Split Recordings Bigger Than (bytes)", DataType.LONG, settings.splitRecordingsBiggerThanBytes, json);
            addParameter("timeoutRecordingStartingAt", "Timeout recording starting at", DataType.TIME, settings.timeoutRecordingStartingAt, json);
            addParameter("timeoutRecordingEndingAt", "Timeout recording until", DataType.TIME, settings.timeoutRecordingEndingAt, json);
            addParameter("transportLayerSecurity", "Transport Layer Security (TLS)", DataType.BOOLEAN, settings.transportLayerSecurity, json);
            addParameter("webinterface", "Web-Interface", DataType.BOOLEAN, settings.webinterface, json);
            addParameter("webinterfaceUsername", "Web-Interface User", DataType.STRING, settings.webinterfaceUsername, json);
            addParameter("webinterfacePassword", "Web-Interface Password", DataType.STRING, settings.webinterfacePassword, json);
            addParameter("servletContext", "Servlet Context", DataType.STRING, settings.servletContext, json);
            addParameter("logFFmpegOutput", "Log FFmpeg Output", DataType.BOOLEAN, settings.logFFmpegOutput, json);
            addParameter("flaresolverr.apiUrl", "Flaresolverr API URL", DataType.STRING, settings.flaresolverr.apiUrl, json);
            addParameter("flaresolverr.timeoutInMillis", "Flaresolverr request timeout (ms)", DataType.INTEGER, settings.flaresolverr.timeoutInMillis, json);
            addParameter("flaresolverr.useForDomains", "Use Flaresolverr for domains (one per line)", DataType.STRING_LIST, settings.flaresolverr.useForDomains, json);

            resp.setStatus(SC_OK);
            resp.setContentType("application/json");
            resp.getWriter().print(json.toString());
        } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e) {
            resp.setStatus(SC_INTERNAL_SERVER_ERROR);
            LOG.error("Error during authentication", e);
        }
    }

    private void addParameter(String key, String name, DataType type, Object value, JSONArray json) {
        JSONObject param = new JSONObject();
        param.put("key", key);
        param.put("name", name);
        param.put("type", type);
        param.put("value", value);
        json.put(param);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
        try {
            resp.setContentType("application/json");
            String body = body(req);
            boolean authenticated = checkAuthentication(req, body);
            if (!authenticated) {
                String response = "HMAC does not match";
                sendResponse(resp, SC_UNAUTHORIZED, response);
                return;
            }

            JSONArray json = new JSONArray(body);
            changeConfig(json);
            sendResponse(resp, SC_OK, "{\"status\": \"success\"}");
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            LOG.error("Couldn't authenticate request", e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, "Couldn't authenticate request");
        } catch (JSONException e) {
            LOG.error("Couldn't parse request", e);
            sendResponse(resp, SC_BAD_REQUEST, "JSON array expected");
        } catch (IOException e) {
            LOG.error("Couldn't read request", e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, "Couldn't read request");
        } catch (ConfigWriteException e) {
            LOG.error("Couldn't write config", e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, "Couldn't write config: " + e.getLocalizedMessage());
        }
    }

    private void changeConfig(JSONArray json) throws ConfigWriteException {
        try {
            for (int i = 0; i < json.length(); i++) {
                JSONObject property = json.getJSONObject(i);
                String key = property.optString("key");
                DataType type = DataType.valueOf(property.optString("type"));
                Object value = property.get("value");
                Object typeCorrectedValue = correctType(type, value);
                LOG.debug("{}: {}", key, value);

                var field = BoundField.of(settings, key);
                field.set(typeCorrectedValue);
            }
            config.save();
        } catch (Exception e) {
            throw new ConfigWriteException(e);
        }
    }

    private Object correctType(DataType type, Object value) {
        Object corrected = value;
        if (value == null) {
            return null;
        }
        switch (type) {
        case INTEGER:
            corrected = Integer.parseInt(value.toString());
            break;
        case LONG:
            corrected = Long.parseLong(value.toString());
            break;
        case BOOLEAN:
            corrected = Boolean.parseBoolean(value.toString());
            break;
        case DOUBLE:
            corrected = Double.parseDouble(value.toString());
            break;
        case SPLIT_STRATEGY:
            corrected = SplitStrategy.valueOf(value.toString());
            break;
        case TIME:
            corrected = LocalTime.parse(value.toString());
            break;
        case STRING_LIST:
            corrected = ((JSONArray)value).toList();
            break;
        default:
            break;
        }
        return corrected;
    }

    private static class ConfigWriteException extends Exception {
        public ConfigWriteException(Exception e) {
            super(e);
        }
    }
}
