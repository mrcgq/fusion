package ctbrec.recorder.server;

import ctbrec.Config;
import ctbrec.image.PortraitStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.File;
import java.nio.file.Files;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.io.HttpConstants.MIMETYPE_IMAGE_JPG;
import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.*;

@Slf4j
@RequiredArgsConstructor
public class ImageServlet extends AbstractCtbrecServlet {

    public static final String BASE_URL = "/image";
    private static final Pattern URL_PATTERN_PORTRAIT_BY_ID = Pattern.compile(BASE_URL + "/portrait/([0-9a-fA-F]{8}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{4}\\b-[0-9a-fA-F]{12})");
    private static final Pattern URL_PATTERN_PORTRAIT_BY_URL = Pattern.compile(BASE_URL + "/portrait/url/(.*)");
    private static final Pattern URL_PATTERN_RECORDING_IMAGE = Pattern.compile(BASE_URL + "/recording/(.*)");
    private final PortraitStore portraitStore;
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
            if ((m = URL_PATTERN_PORTRAIT_BY_ID.matcher(requestURI)).matches()) {
                String portraitId = m.group(1);
                servePortrait(resp, portraitId);
            } else if ((m = URL_PATTERN_PORTRAIT_BY_URL.matcher(requestURI)).matches()) {
                String modelUrl = URLDecoder.decode(m.group(1), UTF_8);
                String portraitId = config.getSettings().modelPortraits.get(modelUrl);
                servePortrait(resp, portraitId);
            } else if ((m = URL_PATTERN_RECORDING_IMAGE.matcher(requestURI)).matches()) {
                String filename = m.group(1);
                serveRecordingImage(resp, filename);
            }
        } catch (Exception e) {
            log.error(INTERNAL_SERVER_ERROR, e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
    }

    private void servePortrait(HttpServletResponse resp, String portraitId) throws IOException {
        Optional<byte[]> imageData = portraitStore.loadModelPortraitById(portraitId);
        if (imageData.isPresent()) {
            resp.setStatus(SC_OK);
            resp.setContentType(MIMETYPE_IMAGE_JPG);
            byte[] b = imageData.get();
            resp.setContentLength(b.length);
            resp.getOutputStream().write(b, 0, b.length);
            resp.getOutputStream().flush();
        } else {
            sendResponse(resp, SC_NOT_FOUND, "Portrait with ID " + portraitId + " not found");
        }
    }

    private void serveRecordingImage(HttpServletResponse resp, String filename) throws IOException {
        String recordingsDir = this.config.getSettings().recordingsDir;
        File imageFile = new File(recordingsDir, filename);

        if (!imageFile.exists() || !imageFile.isFile()) {
            sendResponse(resp, SC_NOT_FOUND, "Image not found");
            return;
        }

        resp.setStatus(SC_OK);
        resp.setContentType(MIMETYPE_IMAGE_JPG);
        Files.copy(imageFile.toPath(), resp.getOutputStream());
        resp.getOutputStream().flush();
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) {
        String requestURI = req.getRequestURI().substring(req.getContextPath().length());
        try {
            byte[] data = bodyAsByteArray(req);
            boolean authenticated = checkAuthentication(req, data);
            if (!authenticated) {
                sendResponse(resp, SC_UNAUTHORIZED, HMAC_ERROR_DOCUMENT);
                return;
            }

            Matcher m;
            if ((m = URL_PATTERN_PORTRAIT_BY_URL.matcher(requestURI)).matches()) {
                String modelUrl = URLDecoder.decode(m.group(1), UTF_8);
                portraitStore.writePortrait(modelUrl, data);
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
            if ((m = URL_PATTERN_PORTRAIT_BY_URL.matcher(requestURI)).matches()) {
                String modelUrl = URLDecoder.decode(m.group(1), UTF_8);
                portraitStore.removePortrait(modelUrl);
            }
        } catch (Exception e) {
            log.error(INTERNAL_SERVER_ERROR, e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
    }
}
