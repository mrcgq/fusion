package ctbrec.recorder.server;
import static javax.servlet.http.HttpServletResponse.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.activation.MimetypesFileTypeMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Recording;
import ctbrec.recorder.Recorder;

public class HlsServlet extends AbstractCtbrecServlet {

    private static final transient Logger LOG = LoggerFactory.getLogger(HlsServlet.class);

    private Recorder recorder;

    public HlsServlet(Recorder recorder) {
        this.recorder = recorder;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String contextPath = getServletContext().getContextPath();
        String request = req.getRequestURI().substring(contextPath.length() + 5);

        try {
            String id = request.split("/")[0];
            Optional<Recording> rec = getRecordingById(id);
            if (!rec.isPresent()) {
                error404(req, resp);
                return;
            }

            String requestedFilePath;
            File requestFile;
            boolean idOnly = request.indexOf('/') < 0;
            if (idOnly) {
                requestFile = rec.get().getPostProcessedFile();
                serveSegment(req, resp, requestFile);
                return;
            } else {
                requestedFilePath = request.substring(request.indexOf('/'));
                requestFile = new File(requestedFilePath);
            }

            if (rec.get().getAssociatedFiles().contains(requestedFilePath)) {
                serveSegment(req, resp, requestFile);
            } else {
                if (requestFile.getName().equals("playlist.m3u8")) {
                    boolean isRequestAuthenticated = checkAuthentication(req, req.getRequestURI());
                    if (!isRequestAuthenticated) {
                        writeResponse(resp, SC_UNAUTHORIZED, "{\"status\": \"error\", \"msg\": \"HMAC does not match\"}");
                        return;
                    }
                    servePlaylist(req, resp, rec.get().getAbsoluteFile());
                } else {
                    requestFile = new File(rec.get().getAbsoluteFile(), requestedFilePath);
                    serveSegment(req, resp, requestFile);
                }
            }
        } catch (InvalidKeyException | NoSuchAlgorithmException | IllegalStateException e1) {
            writeResponse(resp, SC_UNAUTHORIZED, "{\"status\": \"error\", \"msg\": \"Authentication failed\"}");
        }
    }

    private Optional<Recording> getRecordingById(String id) throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        return recorder.getRecordings().stream()
                .filter(r -> Objects.equals(id, r.getId()))
                .findFirst();
    }

    private void writeResponse(HttpServletResponse resp, int code, String body) {
        try {
            resp.setStatus(code);
            resp.getWriter().write(body);
        } catch (IOException e) {
            LOG.error("Couldn't write HTTP response", e);
        }
    }

    private void error404(HttpServletRequest req, HttpServletResponse resp) {
        writeResponse(resp, SC_NOT_FOUND, "{\"status\": \"error\", \"msg\": \"Recording not found\"}");
        resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
    }

    private void serveSegment(HttpServletRequest req, HttpServletResponse resp, File requestedFile) throws IOException {
        MimetypesFileTypeMap map = new MimetypesFileTypeMap();
        String mimetype = map.getContentType(requestedFile);
        LOG.debug("Serving {} as {}", requestedFile.getName(), mimetype);
        serveFile(req, resp, requestedFile, mimetype);
    }

    private void servePlaylist(HttpServletRequest req, HttpServletResponse resp, File requestedFile) throws IOException {
        LOG.debug("Serving playlist {}", requestedFile);
        File playlist = new File(requestedFile, "playlist.m3u8");
        serveFile(req, resp, playlist, "application/x-mpegURL");
    }

    private void serveFile(HttpServletRequest req, HttpServletResponse resp, File file, String contentType) throws IOException {
        ByteRange range = getByteRange(req, file);
        LOG.trace("Serving segment {} range: {}-{}", file.getAbsolutePath(), range.from, range.to);
        resp.setStatus(range.set ? SC_PARTIAL_CONTENT : SC_OK);
        resp.setContentType(contentType);
        try (RandomAccessFile fin = new RandomAccessFile(file, "r")) {
            fin.seek(range.from);
            byte[] buffer = new byte[1024 * 100];
            long bytesLeft = range.to - range.from;
            resp.setContentLengthLong(bytesLeft);
            resp.addHeader("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"");
            if (range.set) {
                resp.setHeader("Content-Range", "bytes " + range.from + '-' + range.to + '/' + file.length());
            }
            int bytesToRead = (int) Math.min(bytesLeft, buffer.length);
            int length = -1;
            while ((length = fin.read(buffer, 0, bytesToRead)) >= 0) {
                bytesLeft -= length;
                resp.getOutputStream().write(buffer, 0, length);
            }
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doGet(req, resp);
    }

    private ByteRange getByteRange(HttpServletRequest req, File file) {
        ByteRange range = new ByteRange();
        range.to = file.length();
        String rangeHeader = req.getHeader("Range");
        if (rangeHeader != null) {
            range.set = true;
            Matcher m = Pattern.compile("bytes=(\\d+)-(\\d+)*").matcher(rangeHeader);
            if (m.find()) {
                range.from = Long.parseLong(m.group(1));
                String to = m.group(2);
                if (to != null && !to.trim().isEmpty()) {
                    range.to = Long.parseLong(to);
                }
            }
        }
        return range;
    }

    class ByteRange {
        boolean set = false;
        long from = 0;
        long to = Long.MAX_VALUE;
    }
}
