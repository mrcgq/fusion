package ctbrec.recorder.server;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.NotLoggedInExcetion;
import ctbrec.Version;
import ctbrec.event.EventBusHolder;
import ctbrec.event.EventHandler;
import ctbrec.event.EventHandlerConfiguration;
import ctbrec.image.LocalPortraitStore;
import ctbrec.recorder.OnlineMonitor;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.SimplifiedLocalRecorder;
import ctbrec.servlet.AbstractDocServlet;
import ctbrec.servlet.MarkdownServlet;
import ctbrec.servlet.SearchServlet;
import ctbrec.servlet.StaticFileServlet;
import ctbrec.sites.Site;
import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.sites.bonga.BongaCams;
import ctbrec.sites.cam4.Cam4;
import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.sites.manyvids.MVLive;
import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.sites.secretfriends.SecretFriends;
import ctbrec.sites.showup.Showup;
import ctbrec.sites.streamate.Streamate;
import ctbrec.sites.streamray.Streamray;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.sites.winktv.WinkTv;
import ctbrec.sites.xlovecam.XloveCam;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.SecuredRedirectHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.security.Credential;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.BindException;
import java.net.URL;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static javax.servlet.http.HttpServletResponse.SC_MOVED_PERMANENTLY;
import static javax.servlet.http.HttpServletResponse.SC_NOT_FOUND;

public class HttpServer {

    private static final int MiB = 1024 * 1024; // NOSONAR
    private static final Logger LOG = LoggerFactory.getLogger(HttpServer.class);
    private final Recorder recorder;
    private final OnlineMonitor onlineMonitor;
    private final Config config;
    private final List<Site> sites = new ArrayList<>();
    private Server server = new Server();

    public HttpServer() throws IOException {
        logEnvironment();
        createSites();
        System.setProperty("ctbrec.server.mode", "1");
        if (System.getProperty("ctbrec.config") == null) {
            System.setProperty("ctbrec.config", "server.json");
        }
        try {
            Config.init(sites);
        } catch (Exception e) {
            LOG.error("Couldn't load config", e);
            System.exit(1);
        }

        addShutdownHook(); // for graceful termination

        registerAlertSystem();

        config = Config.getInstance();
        LOG.info("HMAC authentication is {}", config.getSettings().key != null ? "enabled" : "disabled");

        recorder = new SimplifiedLocalRecorder(config, sites);
        initSites();

        onlineMonitor = new OnlineMonitor(recorder, config);
        onlineMonitor.start();
        startHttpServer();
    }

    private void initSites() {
        for (Site site : sites) {
            if (site.isEnabled()) {
                GlobalThreadPool.submit(() -> {
                    try {
                        site.setRecorder(recorder);
                        site.setConfig(config);
                        site.init();
                        safeLogin(site);
                    } catch (IOException e) {
                        LOG.error("Error while initializing site {}", site.getName(), e);
                    }
                });
            }
        }
    }

    private void safeLogin(Site site) {
        if (site.credentialsAvailable()) {
            try {
                boolean success = site.login();
                if (success) {
                    LOG.info("Successfully logged in to {}", site.getName());
                } else {
                    throw new NotLoggedInExcetion("Login returned false");
                }
            } catch (Exception e) {
                LOG.info("Login to {} failed", site.getName());
            }
        }
    }

    private void createSites() {
        sites.add(new AmateurTv());
        sites.add(new BongaCams());
        sites.add(new Cam4());
        sites.add(new Camsoda());
        sites.add(new Chaturbate());
        sites.add(new CherryTv());
        sites.add(new Dreamcam());
        sites.add(new Fc2Live());
        sites.add(new Flirt4Free());
        sites.add(new LiveJasmin());
        sites.add(new MVLive());
        sites.add(new MyFreeCams());
        sites.add(new SecretFriends());
        sites.add(new Showup());
        sites.add(new Streamate());
        sites.add(new Stripchat());
        sites.add(new Streamray());
        sites.add(new XloveCam());
        sites.add(new WinkTv());
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOG.info("Shutting down");
            if (onlineMonitor != null) {
                onlineMonitor.shutdown();
            }
            if (recorder != null) {
                recorder.shutdown(false);
            }
            try {
                server.stop();
            } catch (Exception e) {
                LOG.error("Couldn't stop HTTP server", e);
            }
            try {
                Config.getInstance().save();
            } catch (IOException e) {
                LOG.error("Couldn't save configuration", e);
            }
            LOG.info("Goodbye!");
        }));
    }

    private void startHttpServer() {
        server = new Server();

        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSendServerVersion(false);
        httpConfig.setSecurePort(this.config.getSettings().httpSecurePort);
        httpConfig.setSecureScheme("https");
        HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfig);

        SslContextFactory sslContextFactory = new SslContextFactory.Server();
        URL keyStoreUrl = getClass().getResource("/keystore.pkcs12");
        String keyStoreSrc = System.getProperty("keystore.file", Objects.requireNonNull(keyStoreUrl).toExternalForm());
        String keyStorePassword = System.getProperty("keystore.password", "ctbrecsucks");
        sslContextFactory.setKeyStorePath(keyStoreSrc);
        sslContextFactory.setKeyStorePassword(keyStorePassword);
        sslContextFactory.setTrustStorePath(keyStoreSrc);
        sslContextFactory.setTrustStorePassword(keyStorePassword);

        try (ServerConnector http = new ServerConnector(server, httpConnectionFactory);
             ServerConnector https = new ServerConnector(server, sslContextFactory, httpConnectionFactory)) {

            // connector for http
            http.setPort(this.config.getSettings().httpPort);
            http.setIdleTimeout(this.config.getSettings().httpTimeout);

            // connector for https (TLS)
            https.setPort(this.config.getSettings().httpSecurePort);
            https.setIdleTimeout(this.config.getSettings().httpTimeout);

            String contextPath = Config.getInstance().getContextPath();
            server.setErrorHandler(createErrorHandler(contextPath));
            ServletContextHandler basicAuthContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
            basicAuthContext.setContextPath(contextPath + "/secured");

            ServletContextHandler defaultContext = new ServletContextHandler(ServletContextHandler.SESSIONS);
            defaultContext.setContextPath(contextPath);
            addHttpHeaderFilter(defaultContext);

            RecorderServlet recorderServlet = new RecorderServlet(recorder, sites);
            ServletHolder holder = new ServletHolder(recorderServlet);
            defaultContext.addServlet(holder, "/rec");

            ConfigServlet configServlet = new ConfigServlet(config);
            holder = new ServletHolder(configServlet);
            defaultContext.addServlet(holder, "/config");

            HlsServlet hlsServlet = new HlsServlet(recorder);
            holder = new ServletHolder(hlsServlet);
            defaultContext.addServlet(holder, "/hls/*");

            var markdownServlet = new MarkdownServlet();
            holder = new ServletHolder(markdownServlet);
            defaultContext.addServlet(holder, "/docs/*");

            AbstractDocServlet searchServlet = new SearchServlet();
            holder = new ServletHolder(searchServlet);
            defaultContext.addServlet(holder, "/search/*");

            LocalPortraitStore portraitStore = new LocalPortraitStore(config);
            ImageServlet imageServlet = new ImageServlet(portraitStore, config);
            holder = new ServletHolder(imageServlet);
            String location;
            try {
                location = File.createTempFile("upload", "").getParentFile().toString();
            } catch (IOException e) {
                location = ".";
            }
            long maxFileSize = 10L * MiB;
            long maxRequestSize = 10L * MiB;
            int fileSizeThreshold = MiB;
            MultipartConfigElement multipartConfig = new MultipartConfigElement(location, maxFileSize, maxRequestSize, fileSizeThreshold);
            holder.getRegistration().setMultipartConfig(multipartConfig);
            defaultContext.addServlet(holder, ImageServlet.BASE_URL + "/*");

            ModelServlet modelServlet = new ModelServlet(config);
            holder = new ServletHolder(modelServlet);
            defaultContext.addServlet(holder, ModelServlet.BASE_URL + "/*");
            
            DebugServlet debugServlet = new DebugServlet(recorder);
            holder = new ServletHolder(debugServlet);
            defaultContext.addServlet(holder, DebugServlet.BASE_URL + "/*");

            if (this.config.getSettings().webinterface) {
                startWebInterface(defaultContext, basicAuthContext);
            }

            server.addConnector(http);
            HandlerList handlers = new HandlerList();
            if (this.config.getSettings().transportLayerSecurity) {
                server.addConnector(https);
                handlers.setHandlers(new Handler[]{new SecuredRedirectHandler(), basicAuthContext, defaultContext});
            } else {
                handlers.setHandlers(new Handler[]{basicAuthContext, defaultContext});
            }
            server.setHandler(handlers);

            try {
                server.start();
                server.join();
            } catch (BindException e) {
                LOG.error("Port {} is already in use", http.getPort(), e);
                System.exit(1);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOG.error("Server start failed", e);
                System.exit(1);
            } catch (Exception e) {
                LOG.error("Server start failed", e);
                System.exit(1);
            }
        }
    }

    private void startWebInterface(ServletContextHandler defaultContext, ServletContextHandler basicAuthContext) {
        StaticFileServlet staticFileServlet = new StaticFileServlet("/html");
        ServletHolder holder = new ServletHolder(staticFileServlet);
        String staticFileContext = "/static/*";
        defaultContext.addServlet(holder, staticFileContext);
        LOG.info("Register static file servlet under {}", defaultContext.getContextPath() + staticFileContext);

        // servlet to retrieve the HMAC (secured by basic auth if a hmac key is set in the config)
        String username = this.config.getSettings().webinterfaceUsername;
        String password = this.config.getSettings().webinterfacePassword;
        if (config.getSettings().key != null && config.getSettings().key.length > 0) {
            basicAuthContext.setSecurityHandler(basicAuth(username, password));
        }
        basicAuthContext.addServlet(new ServletHolder(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
                if (Objects.equals(username, req.getRemoteUser())) {
                    resp.setStatus(HttpServletResponse.SC_OK);
                    resp.setContentType("application/json");
                    byte[] hmac = Optional.ofNullable(HttpServer.this.config.getSettings().key).orElse(new byte[0]);
                    try {
                        JSONObject response = new JSONObject();
                        response.put("hmac", new String(hmac, UTF_8));
                        resp.getOutputStream().println(response.toString());
                    } catch (Exception e) {
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                    }
                }
            }
        }), "/hmac");
    }

    private ErrorHandler createErrorHandler(String contextPath) {
        return new ErrorHandler() {
            @Override
            public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
                if (response.getStatus() == SC_NOT_FOUND && (request.getRequestURI().equals("/") || request.getRequestURI().equals("/index.html"))) {
                    response.setStatus(SC_MOVED_PERMANENTLY);
                    response.setHeader("Location", contextPath + "/static/index.html");
                } else {
                    super.handle(target, baseRequest, request, response);
                }
            }

            @Override
            protected void handleErrorPage(HttpServletRequest request, Writer writer, int code, String message) throws IOException {
                if (code == SC_NOT_FOUND) {
                    writer.write("<html><head><title>404</title><style>* {font-family: sans-serif}</style></head><body><h1>404</h1><p>Looking for <a href=\"" + contextPath + "/static/index.html\">CTB Recorder</a>?</p></body>");
                } else {
                    super.handleErrorPage(request, writer, code, message);
                }
            }
        };
    }

    private void addHttpHeaderFilter(ServletContextHandler defaultContext) {
        FilterHolder httpHeaderFilter = new FilterHolder(new Filter() {
            @Override
            public void init(FilterConfig filterConfig) {
                // noop
            }

            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                ((HttpServletResponse) response).addHeader("Server", "CTB Recorder/" + getVersion());
                chain.doFilter(request, response);
            }

            @Override
            public void destroy() {
                // noop
            }
        });
        defaultContext.addFilter(httpHeaderFilter, "/*", EnumSet.of(DispatcherType.REQUEST, DispatcherType.INCLUDE));
    }

    private static SecurityHandler basicAuth(String username, String password) {
        String realm = "CTB Recorder";
        UserStore userStore = new UserStore();
        userStore.addUser(username, Credential.getCredential(password), new String[]{"user"});
        HashLoginService l = new HashLoginService();
        l.setUserStore(userStore);
        l.setName(realm);

        Constraint constraint = new Constraint();
        constraint.setName(Constraint.__BASIC_AUTH);
        constraint.setRoles(new String[]{"user"});
        constraint.setAuthenticate(true);

        ConstraintMapping cm = new ConstraintMapping();
        cm.setConstraint(constraint);
        cm.setPathSpec("/*");

        ConstraintSecurityHandler csh = new ConstraintSecurityHandler();
        csh.setAuthenticator(new BasicAuthenticator());
        csh.setRealmName(realm);
        csh.addConstraintMapping(cm);
        csh.setLoginService(l);

        return csh;
    }

    private void registerAlertSystem() {
        for (EventHandlerConfiguration ehconfig : Config.getInstance().getSettings().eventHandlers) {
            EventHandler handler = new EventHandler(ehconfig);
            EventBusHolder.register(handler);
            LOG.debug("Registered event handler for {} {}", ehconfig.getEvent(), ehconfig.getName());
        }
        LOG.debug("Alert System registered");
    }

    private void logEnvironment() {
        LOG.debug("OS:\t{} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        LOG.debug("Java:\t{} {} {}", System.getProperty("java.vendor"), System.getProperty("java.vm.name"), System.getProperty("java.version"));
        try {
            LOG.debug("ctbrec server {}", getVersion());
        } catch (IOException e) {
            // nothing to do here
        }
    }

    private Version getVersion() throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("version")) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(is)));
            String versionString = reader.readLine();
            Version version = Version.of(versionString);
            return version;
        }
    }

    public static void main(String[] args) throws Exception {
        new HttpServer();
    }
}
