package ctbrec.servlet;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class StaticFileServlet extends HttpServlet {

    private final String classPathRoot;
    private final Map<String, String> mimetypes = new HashMap<>();
    private final boolean contextAware;

    public StaticFileServlet(String classPathRoot) {
        this(classPathRoot, true);
    }

    public StaticFileServlet(String classPathRoot, boolean contextAware) {
        this.classPathRoot = classPathRoot;
        this.contextAware = contextAware;
        mimetypes.put("css", "text/css");
        mimetypes.put("js", "application/javascript");
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        try {
            String request = req.getRequestURI();
            if (contextAware) {
                String contextPath = getServletContext().getContextPath();
                request = request.substring(contextPath.length());
            }
            serveFile(request, resp);
        } catch (FileNotFoundException e) {
            req.getRequestDispatcher("/404").forward(req, resp);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException, ServletException {
        doGet(req, resp);
    }

    private void serveFile(String resource, HttpServletResponse resp) throws IOException {
        try (InputStream resourceAsStream = getClass().getResourceAsStream(classPathRoot + resource)) {
            if (resourceAsStream == null) {
                resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
                throw new FileNotFoundException();
            }

            String mimetype = URLConnection.guessContentTypeFromName(resource);
            if (mimetype == null) {
                mimetype = guessMimeType(resource);
            }
            resp.setContentType(mimetype);
            resp.setStatus(HttpServletResponse.SC_OK);
            OutputStream out = resp.getOutputStream();
            int length;
            byte[] buffer = new byte[1024];
            while ((length = resourceAsStream.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
        }
    }

    private String guessMimeType(String resource) {
        try {
            String extension = resource.substring(resource.lastIndexOf('.') + 1);
            return mimetypes.get(extension);
        } catch (Exception e) {
            // fail silently
        }
        return null;
    }
}
