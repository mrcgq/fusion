package ctbrec.servlet;

import com.google.common.base.Objects;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.options.MutableDataSet;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

@Slf4j
public class MarkdownServlet extends AbstractDocServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
        String path = req.getRequestURI();
        log.trace("Path: [{}]", path);
        try {
            if (Objects.equal(path, getBaseDir() + "/index.md")) {
                listPages(resp);
            } else {
                String md = loadMarkdown(path);
                String html = markdownToHtml(md);
                resp.setStatus(HttpServletResponse.SC_OK);
                resp.getWriter().println(getHeader());
                resp.getWriter().println(html);
                resp.getWriter().println(getFooter());
            }
        } catch (FileNotFoundException e) {
            log.error("Error loading markdown page", e);
            error(resp, HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            log.error("Error loading markdown page", e);
            error(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private void listPages(HttpServletResponse resp) throws IOException {
        List<String> pages = getPages();
        var html = new StringBuilder("<ul>");
        for (String page : pages) {
            html.append("<li><a href=\"").append(getBaseDir()).append("/").append(page).append("\">").append(page).append("</a></li>");
        }
        html.append("</ul>");
        resp.setStatus(HttpServletResponse.SC_OK);
        resp.getWriter().println(getHeader());
        resp.getWriter().println(html);
        resp.getWriter().println(getFooter());
    }

    private String markdownToHtml(String markdown) {
        var options = new MutableDataSet();
        var parser = Parser.builder(options).build();
        HtmlRenderer renderer = HtmlRenderer.builder(options).build();
        Node document = parser.parse(markdown);
        String html = renderer.render(document);
        return html;
    }
}
