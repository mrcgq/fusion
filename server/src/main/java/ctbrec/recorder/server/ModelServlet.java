package ctbrec.recorder.server;

import ctbrec.Config;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.*;

@Slf4j
@RequiredArgsConstructor
public class ModelServlet extends AbstractCtbrecServlet {

    public static final String BASE_URL = "/models";

    private static final Pattern URL_PATTERN_ALL_MODEL_NOTES = Pattern.compile(BASE_URL + "/notes/?");
    private static final Pattern URL_PATTERN_MODEL_NOTES = Pattern.compile(BASE_URL + "/notes/(.+?)");
    private final Config config;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String requestURI = req.getRequestURI().substring(req.getContextPath().length());
        try {
            boolean authenticated = checkAuthentication(req, "");
            if (!authenticated) {
                sendResponse(resp, SC_UNAUTHORIZED, HMAC_ERROR_DOCUMENT);
                return;
            }

            Matcher m;
            if ((m = URL_PATTERN_MODEL_NOTES.matcher(requestURI)).matches()) {
                String modelUrl = URLDecoder.decode(m.group(1), UTF_8);
                String notes = config.getSettings().modelNotes.getOrDefault(modelUrl, "");
                log.debug("Model Notes Request {} - {}", modelUrl, notes);
                resp.setContentType("text/plain");
                sendResponse(resp, SC_OK, notes);
            } else if ((URL_PATTERN_ALL_MODEL_NOTES.matcher(requestURI)).matches()) {
                JSONObject notes = new JSONObject();
                config.getSettings().modelNotes.forEach(notes::put);
                resp.setContentType("application/json");
                sendResponse(resp, SC_OK, notes.toString());
            }
        } catch (Exception e) {
            log.error(INTERNAL_SERVER_ERROR, e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String requestURI = req.getRequestURI().substring(req.getContextPath().length());
        try {
            var body = body(req);
            log.info("Notes: [{}]", body);
            boolean authenticated = checkAuthentication(req, body);
            if (!authenticated) {
                sendResponse(resp, SC_UNAUTHORIZED, HMAC_ERROR_DOCUMENT);
                return;
            }

            Matcher m;
            if ((m = URL_PATTERN_MODEL_NOTES.matcher(requestURI)).matches()) {
                String modelUrl = URLDecoder.decode(m.group(1), UTF_8);
                config.getSettings().modelNotes.put(modelUrl, body);
                config.save();
            }
        } catch (Exception e) {
            log.error(INTERNAL_SERVER_ERROR, e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp) {
        String requestURI = req.getRequestURI().substring(req.getContextPath().length());
        try {
            boolean authenticated = checkAuthentication(req, "");
            if (!authenticated) {
                sendResponse(resp, SC_UNAUTHORIZED, HMAC_ERROR_DOCUMENT);
                return;
            }

            Matcher m;
            if ((m = URL_PATTERN_MODEL_NOTES.matcher(requestURI)).matches()) {
                String modelUrl = URLDecoder.decode(m.group(1), UTF_8);
                config.getSettings().modelNotes.remove(modelUrl);
                config.save();
            }
        } catch (Exception e) {
            log.error(INTERNAL_SERVER_ERROR, e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
    }
}
