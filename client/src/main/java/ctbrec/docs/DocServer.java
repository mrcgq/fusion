package ctbrec.docs;

import ctbrec.servlet.AbstractDocServlet;
import ctbrec.servlet.MarkdownServlet;
import ctbrec.servlet.SearchServlet;
import ctbrec.servlet.StaticFileServlet;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.component.LifeCycle;

import java.net.BindException;

@Slf4j
public class DocServer {

    private static volatile boolean started = false;

    private DocServer() {
    }

    public static synchronized void start() throws Exception {
        start(() -> {
        });
    }

    public static synchronized void start(Runnable callback) throws Exception {
        log.info("DocServer.start");
        if (started) {
            log.info("if started");
            callback.run();
            return;
        }

        started = true;
        var server = new Server();

        var config = new HttpConfiguration();
        config.setSendServerVersion(false);
        var http = new ServerConnector(server, new HttpConnectionFactory(config));
        http.setPort(5689);
        server.addConnector(http);

        var handler = new ServletHandler();
        server.setHandler(handler);
        var handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{handler});
        server.setHandler(handlers);

        var markdownServlet = new MarkdownServlet();
        var holder = new ServletHolder(markdownServlet);
        handler.addServletWithMapping(holder, "/docs/*");

        AbstractDocServlet searchServlet = new SearchServlet();
        holder = new ServletHolder(searchServlet);
        handler.addServletWithMapping(holder, "/search/*");

        var staticFileServlet = new StaticFileServlet("/html", false);
        holder = new ServletHolder(staticFileServlet);
        handler.addServletWithMapping(holder, "/static/*");

        server.addLifeCycleListener(new LifeCycle.Listener() {
            @Override
            public void lifeCycleStarted(LifeCycle event) {
                callback.run();
            }
        });

        try {
            server.start();
            server.join();
        } catch (BindException e) {
            log.error("Port {} is already in use", http.getPort(), e);
        }
    }
}
