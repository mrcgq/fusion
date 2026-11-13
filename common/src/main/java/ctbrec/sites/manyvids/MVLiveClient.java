package ctbrec.sites.manyvids;

import static ctbrec.StringUtil.*;
import static ctbrec.io.HttpConstants.*;
import static ctbrec.sites.manyvids.MVLive.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Objects;

import ctbrec.Config;
import ctbrec.sites.manyvids.wsmsg.GetBroadcastHealth;
import ctbrec.sites.manyvids.wsmsg.Message;
import ctbrec.sites.manyvids.wsmsg.Ping;
import ctbrec.sites.manyvids.wsmsg.RegisterMessage;
import ctbrec.sites.manyvids.wsmsg.SendMessage;
import okhttp3.Cookie;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class MVLiveClient {

    private static final Logger LOG = LoggerFactory.getLogger(MVLiveClient.class);

    private final Map<String, Message> futureResponses = new HashMap<>();
    private final MVLiveHttpClient httpClient;
    private final Object streamUrlMonitor = new Object();
    private final Random rng = new Random();

    private WebSocket ws;
    private volatile boolean running = false;
    private volatile boolean connecting = false;
    private String masterPlaylist = null;
    private String roomNumber;
    private String roomId;
    private ScheduledExecutorService scheduler;

    public MVLiveClient(MVLiveHttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public void start(MVLiveModel model) throws IOException {
        running = true;

        if (ws == null && !connecting) {
            httpClient.fetchAuthenticationCookies();
            JSONObject response = model.getRoomLocation();
            roomNumber = response.optString("floorId");
            roomId = response.optString("roomId");
            int randomNumber = 100 + rng.nextInt(800);
            String randomString = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
            String wsUrl = model.getApiUrl().replace("https", "wss");
            String url = String.format("%s/%s/eventbus/%s/%s/websocket", wsUrl, roomNumber, randomNumber, randomString);

            LOG.info("Websocket is null. Starting a new connection to {}", url);
            ws = createWebSocket(url, roomId, model.getDisplayName());
        }
    }

    private String getPhpSessionIdCookie() {
        List<Cookie> cookies = httpClient.getCookiesByName("PHPSESSID");
        return cookies.stream().map(c -> c.name() + "=" + c.value()).findFirst().orElse("");
    }

    public void stop() {
        running = false;
        Optional.ofNullable(scheduler).ifPresent(ScheduledExecutorService::shutdown);
        ws.close(1000, "Good Bye"); // terminate normally (1000)
        ws = null;
    }

    private WebSocket createWebSocket(String wsUrl, String roomId, String modelName) {
        connecting = true;
        Request req = new Request.Builder()
                .url(wsUrl)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ORIGIN, WS_ORIGIN)
                .header(COOKIE, getPhpSessionIdCookie())
                .build();
        return httpClient.newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                try {
                    connecting = false;
                    LOG.trace("WS open: [{}]", response.body().string());
                    scheduler = new ScheduledThreadPoolExecutor(1);
                    scheduler.scheduleAtFixedRate(() -> sendMessages(new Ping()), 5, 5, TimeUnit.SECONDS);
                } catch (IOException e) {
                    LOG.error("Error while processing onOpen event", e);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                connecting = false;
                LOG.trace("MVLive websocket closed: {} {}", code, reason);
                MVLiveClient.this.ws = null;
                running = false;
                synchronized (streamUrlMonitor) {
                    streamUrlMonitor.notifyAll();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                running = false;
                synchronized (streamUrlMonitor) {
                    streamUrlMonitor.notifyAll();
                }
                connecting = false;
                if (response != null) {
                    int code = response.code();
                    String message = response.message();
                    LOG.error("MVLive websocket failure: {} {}", code, message, t);
                    response.close();
                } else {
                    LOG.error("MVLive websocket failure", t);
                }
                MVLiveClient.this.ws = null;
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                LOG.trace("Message: {}", text);
                text = Optional.ofNullable(text).orElse("");
                if (Objects.equal("o", text)) {
                    sendMessages(new Ping());
                    sendMessages(new GetBroadcastHealth(roomId, modelName, (m, r) -> {
                        LOG.trace("--> {}", m);
                        LOG.trace("<-- {}", r);
                        String addr = r.getJSONObject("body").optString("subscribeAddress");
                        sendMessages(new RegisterMessage(addr, (mr, rr) -> {
                            LOG.trace("--> {}", mr);
                            LOG.trace("<-- {}", rr);
                            masterPlaylist = rr.getJSONObject("body").optString("videoUrl");
                            LOG.trace("Got the URL: {}", masterPlaylist);
                            stop();
                            synchronized (streamUrlMonitor) {
                                streamUrlMonitor.notifyAll();
                            }
                        }));
                    }));
                } else if (text.startsWith("a")) {
                    JSONArray jsonArray = new JSONArray(text.substring(1));
                    for (int i = 0; i < jsonArray.length(); i++) {
                        String respJson = jsonArray.getString(i);
                        JSONObject response = new JSONObject(respJson);
                        String address = response.optString("address");
                        if (isNotBlank(address)) {
                            Message message = futureResponses.get(address);
                            if (message != null) {
                                message.handleResponse(response);
                                if (!(message instanceof RegisterMessage)) {
                                    futureResponses.remove(address);
                                }
                            }
                        }
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
                LOG.trace("Binary Message: {}", bytes.hex());
            }
        });
    }

    void sendMessages(Message... messages) {
        JSONArray msgs = new JSONArray();
        for (Message msg : messages) {
            msgs.put(msg.toString());
            if (msg instanceof SendMessage) {
                SendMessage sendMessage = (SendMessage) msg;
                futureResponses.put(sendMessage.getReplyAddress(), sendMessage);
            } else if (msg instanceof RegisterMessage) {
                RegisterMessage registerMessage = (RegisterMessage) msg;
                futureResponses.put(registerMessage.getAddress(), registerMessage);
            }
        }
        ws.send(msgs.toString());
    }

    public StreamLocation getStreamLocation(MVLiveModel model) throws IOException, InterruptedException {
        start(model);
        while (running) {
            synchronized (streamUrlMonitor) {
                streamUrlMonitor.wait(TimeUnit.SECONDS.toMillis(20000));
                break;
            }
        }
        if (ws != null) {
            stop();
        }
        return new StreamLocation(roomId, roomNumber, masterPlaylist);
    }
}
