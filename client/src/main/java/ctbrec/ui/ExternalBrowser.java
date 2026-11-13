package ctbrec.ui;

import ctbrec.Config;
import ctbrec.OS;
import ctbrec.io.ProcessOutputLogger;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class ExternalBrowser implements AutoCloseable { // NOSONAR singleton is wanted
    private static final ExternalBrowser INSTANCE = new ExternalBrowser();
    private static final String MSGID = "msgid";
    private final Lock lock = new ReentrantLock();
    private Socket socket;

    private Consumer<String> messageListener;
    private InputStream in;
    private OutputStream out;
    private Thread reader;
    private volatile boolean stopped = true;
    private volatile boolean browserReady = false;
    private final Object browserReadyLock = new Object();

    private final Map<String, CompletableFuture<Object>> responseFutures = new HashMap<>();
    private Runnable onReadyCallback;

    public static ExternalBrowser getInstance() {
        return INSTANCE;
    }

    public void run(JSONObject jsonConfig, Consumer<String> messageListener) throws InterruptedException, IOException {
        log.debug("Running browser with config {}", jsonConfig);
        lock.lock();
        try {
            stopped = false;
            this.messageListener = messageListener;

            addProxyConfig(jsonConfig.getJSONObject("config"));

            var configDir = new File(Config.getInstance().getConfigDir(), "ctbrec-minimal-browser");
            String[] cmdline = OS.getBrowserCommand(configDir.getCanonicalPath());
            Process p = new ProcessBuilder(cmdline).start();
            new Thread(new ProcessOutputLogger(p.getInputStream(), "ExternalBrowser stdout")).start();
            new Thread(new ProcessOutputLogger(p.getErrorStream(), "ExternalBrowser stderr")).start();
            log.debug("Browser started: {}", Arrays.toString(cmdline));

            connectToRemoteControlSocket();
            while (!browserReady) {
                synchronized (browserReadyLock) {
                    browserReadyLock.wait(100);
                }
            }
            if (log.isTraceEnabled()) {
                log.debug("Connected to remote control server. Sending config {}", jsonConfig);
            } else {
                log.debug("Connected to remote control server. Sending config");
            }
            out.write(jsonConfig.toString().getBytes(UTF_8));
            out.write('\n');
            out.flush();

            Optional.ofNullable(onReadyCallback).ifPresent(Runnable::run);

            log.debug("Waiting for browser to terminate");
            p.waitFor();
            int exitValue = p.exitValue();
            reader = null;
            in = null;
            out = null;
            this.messageListener = null;
            log.debug("Browser Process terminated with {}", exitValue);
        } finally {
            lock.unlock();
        }
    }

    private void connectToRemoteControlSocket() throws IOException {
        for (var i = 0; i < 20; i++) {
            try {
                socket = new Socket("localhost", 3202);
                in = socket.getInputStream();
                out = socket.getOutputStream();
                reader = new Thread(this::readBrowserOutput);
                reader.start();
                log.debug("Connected to control socket");
                return;
            } catch (IOException e) {
                if (i == 19) {
                    log.error("Connection to remote control socket failed", e);
                    throw e;
                }
            }

            // wait a bit, so that the remote server socket can be opened by electron
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public CompletableFuture<Object> executeJavaScript(String javaScript) throws IOException {
        String id = UUID.randomUUID().toString();
        var future = new CompletableFuture<>();
        var script = new JSONObject();
        script.put(MSGID, id);
        script.put("execute", javaScript);
        if (out != null) {
            out.write(script.toString().getBytes(UTF_8));
            out.write('\n');
            out.flush();
            responseFutures.put(id, future);
        }
        if (javaScript.equals("quit")) {
            stopped = true;
        }
        return future;
    }

    @Override
    public void close() throws IOException {
        if (stopped) {
            return;
        }
        stopped = true;
        executeJavaScript("quit");
    }

    private void readBrowserOutput() {
        log.debug("Browser output reader started");
        try (var br = new BufferedReader(new InputStreamReader(in))) {
            String line;
            synchronized (browserReadyLock) {
                browserReady = true;
                browserReadyLock.notifyAll();
            }
            while (!Thread.interrupted() && (line = br.readLine()) != null) {
                log.debug("Browser output: {}", line);
                if (line.startsWith("{")) {
                    JSONObject json = new JSONObject(line);
                    if (json.has(MSGID)) {
                        handleExecuteScriptResponse(json);
                    } else {
                        if (messageListener != null) {
                            messageListener.accept(line);
                        }
                    }
                }
            }
        } catch (IOException e) {
            if (!stopped) {
                log.error("Couldn't read browser output", e);
            }
        } finally {
            stopped = true;
            synchronized (browserReadyLock) {
                browserReady = true;
                browserReadyLock.notifyAll();
            }
        }
    }

    private void handleExecuteScriptResponse(JSONObject json) {
        var msgid = json.getString(MSGID);
        log.debug("Future {}", msgid);
        CompletableFuture<Object> future = responseFutures.get(msgid);
        if (future != null) {
            responseFutures.remove(msgid);
            final String RESULT = "result";
            if (json.has(RESULT)) {
                log.debug("Future {} done. Result: {}", msgid, json.get(RESULT));
                future.complete(json.get(RESULT));
            } else if (json.has("error")) {
                log.debug("Future {} failed", msgid);
                future.completeExceptionally(new Exception(json.getJSONObject("error").toString()));
            }
        } else {
            log.warn("No future for previous request {}", msgid);
        }
    }

    private void addProxyConfig(JSONObject jsonConfig) {
        var proxyType = Config.getInstance().getSettings().proxyType;
        var proxy = new JSONObject();
        final String KEY_ADDRESS = "address";
        final String KEY_PROXY = "proxy";
        switch (proxyType) {
            case HTTP:
                proxy.put(KEY_ADDRESS,
                        "http=" + Config.getInstance().getSettings().proxyHost + ':' + Config.getInstance().getSettings().proxyPort
                                + ";https=" + Config.getInstance().getSettings().proxyHost + ':' + Config.getInstance().getSettings().proxyPort);
                if (Config.getInstance().getSettings().proxyUser != null && !Config.getInstance().getSettings().proxyUser.isEmpty()) {
                    String username = Config.getInstance().getSettings().proxyUser;
                    String password = Config.getInstance().getSettings().proxyPassword;
                    proxy.put("user", username);
                    proxy.put("password", password);
                }
                jsonConfig.put(KEY_PROXY, proxy);
                break;
            case SOCKS4:
                proxy = new JSONObject();
                proxy.put(KEY_ADDRESS, "socks4://" + Config.getInstance().getSettings().proxyHost + ':' + Config.getInstance().getSettings().proxyPort);
                jsonConfig.put(KEY_PROXY, proxy);
                break;
            case SOCKS5:
                proxy = new JSONObject();
                proxy.put(KEY_ADDRESS, "socks5://" + Config.getInstance().getSettings().proxyHost + ':' + Config.getInstance().getSettings().proxyPort);
                if (Config.getInstance().getSettings().proxyUser != null && !Config.getInstance().getSettings().proxyUser.isEmpty()) {
                    String username = Config.getInstance().getSettings().proxyUser;
                    String password = Config.getInstance().getSettings().proxyPassword;
                    proxy.put("user", username);
                    proxy.put("password", password);
                }
                jsonConfig.put(KEY_PROXY, proxy);
                break;
            case DIRECT:
            default:
                // nothing to do here
                break;
        }
    }

    public ExternalBrowser onReady(Runnable onReadyCallback) {
        this.onReadyCallback = onReadyCallback;
        return this;
    }
}
