package ctbrec.servlet;

import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public abstract class AbstractDocServlet extends HttpServlet {

    public static final String CLASSPATH_DIR = "/docs";
    private static final Pattern CONTEXT_AWARE_URL = Pattern.compile("@\\{(.*?)\\}");

    String loadFile(String resource) throws IOException {
        try (InputStream resourceAsStream = getClass().getResourceAsStream(resource)) {
            if (resourceAsStream == null) {
                throw new FileNotFoundException();
            }
            var out = new ByteArrayOutputStream();
            var length = 0;
            var buffer = new byte[1024];
            while ((length = resourceAsStream.read(buffer)) >= 0) {
                out.write(buffer, 0, length);
            }
            return out.toString(UTF_8);
        }
    }

    protected String getBaseDir() {
        return Optional.ofNullable(getServletContext().getContextPath()).orElse("") + CLASSPATH_DIR;
    }

    String getHeader() throws IOException {
        return renderContextAwareUris(loadFile(CLASSPATH_DIR + "/header.html"));
    }

    String getFooter() throws IOException {
        return renderContextAwareUris(loadFile(CLASSPATH_DIR + "/footer.html"));
    }

    private String renderContextAwareUris(String s) {
        Matcher m = CONTEXT_AWARE_URL.matcher(s);
        var contextPath = Optional.ofNullable(getServletContext().getContextPath()).orElse("");
        return m.replaceAll(matchResult -> contextPath + matchResult.group(1));
    }

    List<String> getPages() throws IOException {
        List<String> pages = new ArrayList<>();
        URL resource = getClass().getResource(CLASSPATH_DIR);
        if (Objects.equals(resource.getProtocol(), "file")) {
            log.trace("FILE {}", resource);
            indexDirectory(resource, pages);
        } else if (Objects.equals(resource.getProtocol(), "jar")) {
            log.trace("JAR {}", resource);
            indexJar(resource, pages);
        }
        pages.add("index.md");
        pages.sort(String::compareToIgnoreCase);
        return pages;
    }

    private void indexJar(URL resource, List<String> pages) throws IOException {
        String fileUrl = resource.getFile();
        fileUrl = URLDecoder.decode(fileUrl, UTF_8);
        int colon = fileUrl.indexOf(':');
        int exclamation = fileUrl.indexOf('!');
        var jar = fileUrl.substring(colon + 1, exclamation);
        var internalFile = fileUrl.substring(exclamation + 2);
        try (var jarFile = new JarFile(jar)) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                var jarEntry = entries.nextElement();
                String name = jarEntry.getName();
                if (name.startsWith(internalFile) && name.toLowerCase().endsWith(".md")) {
                    pages.add(name.substring(name.lastIndexOf('/') + 1));
                }
            }
        }
    }

    private void indexDirectory(URL resource, List<String> pages) {
        var docs = new File(resource.getFile());
        String[] files = docs.list((dir, name) -> name.toLowerCase().endsWith(".md"));
        pages.addAll(Arrays.asList(files));
    }

    String loadMarkdown(String path) throws IOException {
        var contextPath = getServletContext().getContextPath();
        if (contextPath != null && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return loadFile(path);
    }

    protected void error(HttpServletResponse resp, int status, String message) {
        try {
            resp.setStatus(status);
            resp.getWriter().println(getHeader());
            String html = loadFile(CLASSPATH_DIR + "/" + status + ".html");
            if (message == null || message.trim().isEmpty()) {
                message = "";
            }
            html = html.replace("{message}", message);
            html = renderContextAwareUris(html);
            resp.getWriter().println(html);
            resp.getWriter().println(getFooter());
        } catch (IOException e) {
            log.error("Error while sending error response. Man, his is bad!", e);
        }
    }
}
