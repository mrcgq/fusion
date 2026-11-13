package ctbrec.recorder.server;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.SimplifiedLocalRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URLDecoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.MessageFormat;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.*;

@Slf4j
public class DebugServlet extends AbstractCtbrecServlet {

    public static final String BASE_URL = "/debug";
    
    private static final Pattern URL_PATTERN_DEBUG_STACK = Pattern.compile(BASE_URL + "/stack(/.*?)");
    private static final Pattern URL_PATTERN_DEBUG_STATS = Pattern.compile(BASE_URL + "/stats");
    
    protected Recorder recorder;
    
    public DebugServlet(Recorder rec)
    {
        this.recorder = rec;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String requestURI = req.getRequestURI().substring(req.getContextPath().length());
        try {
            // boolean authenticated = checkAuthentication(req, "");
            // if (!authenticated) {
            //     sendResponse(resp, SC_UNAUTHORIZED, HMAC_ERROR_DOCUMENT);
            //     return;
            // }

            Matcher m;
            if ((m = URL_PATTERN_DEBUG_STACK.matcher(requestURI)).matches()) {
                String threadUrl = URLDecoder.decode(m.group(1), UTF_8);
                
                var stacks = Thread.getAllStackTraces();
                var box = new Object() { String text = ""; };//stacks.toString();
                
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                // e.printStackTrace(pw);
                
                stacks.forEach((thread, stack) -> {
                    box.text += String.format("%s:\n", thread.getName());
                    var idx = 0;
                    for (var s : stack) {
                        box.text += String.format("[%d] %s\n", idx++, s.toString());
                    }
                });
                
                log.debug("Stacks Request {}", threadUrl);
                resp.setContentType("text/plain");
                sendResponse(resp, SC_OK, box.text);
            } else if ((m = URL_PATTERN_DEBUG_STATS.matcher(requestURI)).matches()) {
                // <meta http-equiv=\"refresh\" content=\"1\">
                String text = "<html><body style=\"font-family: &quot;Noto Sans Mono&quot;, monospace;\">";
                text += String.format("GLOBAL_HTTP_CONN_POOL: connectionCount=%d, idleConnectionCount=%d\n", 
                                        HttpClient.getGLOBAL_HTTP_CONN_POOL().connectionCount(),
                                        HttpClient.getGLOBAL_HTTP_CONN_POOL().idleConnectionCount());
                                        
                
                var rlock = SimplifiedLocalRecorder.STATS.statsLock.readLock();
                rlock.lock();
                try {
                    text += String.format("Time between download iterations = %s\n", SimplifiedLocalRecorder.STATS.stats.toString());
                    text += String.format("Error count: %d\n", SimplifiedLocalRecorder.STATS.errorCount);
                } finally {
                    rlock.unlock();
                }
                
                text += "\nRecording stats:\n<table>";                
                text = text.replace("\n", "<br>");
                
                int totalModels = 0;
                
                for (var rec : recorder.getRecordings()) {
                    var proc = rec.getRecordingProcess();
                    if (proc == null) continue;
                    text += String.format("<tr><td>%s</td><td>%s</td></tr>\n", proc.getModel().getDisplayName(), proc.getStats().replace(" ", "&nbsp;"));
                    totalModels++;
                }
                
                text += "</table>";
                text += String.format("<br>Total: %d", totalModels);
                text += "</body></html>";
                
                log.debug("Stats Request");
                resp.setContentType("text/html");
                sendResponse(resp, SC_OK, text);
            } else
                sendResponse(resp, SC_NOT_FOUND, "");
        } catch (Exception e) {
            log.error(INTERNAL_SERVER_ERROR, e);
            sendResponse(resp, SC_INTERNAL_SERVER_ERROR, INTERNAL_SERVER_ERROR);
        }
    }

}
