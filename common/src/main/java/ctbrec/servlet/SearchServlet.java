package ctbrec.servlet;

import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;

public class SearchServlet extends AbstractDocServlet {
    private static final Logger LOG = LoggerFactory.getLogger(SearchServlet.class);
    private static final String Q = "term";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        try {
            if (req.getParameter(Q) == null) {
                error(resp, HttpServletResponse.SC_BAD_REQUEST, "Parameter \"" + Q + "\" is missing");
                return;
            }

            resp.setStatus(HttpServletResponse.SC_OK);
            resp.setContentType("application/json");
            var result = new JSONArray();
            var pages = getPages();

            String q = req.getParameter(Q).toLowerCase();
            String[] tokens = q.split("\\s+");
            searchPages(result, pages, tokens);
            resp.getWriter().println(result);
        } catch (Exception e) {
            try {
                resp.sendError(SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
            } catch (IOException ioe) {
                LOG.error("Error while sending error response", ioe);
            }
        }
    }

    private void searchPages(JSONArray result, List<String> pages, String[] tokens) throws IOException {
        for (String page : pages) {
            try {
                String content = loadMarkdown(CLASSPATH_DIR + "/" + page).toLowerCase();
                var allFound = true;
                for (String token : tokens) {
                    if (!content.contains(token)) {
                        allFound = false;
                        break;
                    }
                }
                if (allFound) {
                    result.put(page);
                }
            } catch (FileNotFoundException e) {
                // virtual page like index.md -> ignore
            }
        }
    }
}
