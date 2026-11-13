package ctbrec.sites.cam4;

import static ctbrec.io.HttpConstants.*;

import java.io.EOFException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.io.HttpException;
import ctbrec.sites.ModelOfflineException;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class Cam4WsClient {

    private static final Logger LOG = LoggerFactory.getLogger(Cam4WsClient.class);

    private Cam4 site;
    private Cam4Model model;
    private Config config;
    private String shard;
    private String token;
    private WebSocket websocket;
    private int r = 1;
    private Map<String, CompletableFuture<String>> responseFuturesByPath = new HashMap<>();
    private Map<Integer, CompletableFuture<String>> responseFuturesBySequence = new HashMap<>();

    public Cam4WsClient(Config config, Cam4 site, Cam4Model model) {
        this.config = config;
        this.site = site;
        this.model = model;
    }

    public JSONObject getRoomState() throws IOException {
        requestAccessToken();
        if (connectAndAuthorize()) {
            return requestRoomState();
        } else {
            throw new IOException("Connect or authorize failed");
        }
    }

    private JSONObject requestRoomState() throws IOException {
        String p = "chatRooms/" + model.getName() + "/roomState";
        CompletableFuture<String> roomStateFuture = send(p, "{\"t\":\"d\",\"d\":{\"r\":" + (r++) + ",\"a\":\"q\",\"b\":{\"p\":\"" + p + "\",\"h\":\"\"}}}");
        try {
            JSONObject roomState = parseRoomStateResponse(roomStateFuture.get(1, TimeUnit.SECONDS));
            websocket.close(1000, "");
            return roomState;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while getting room state with websocket");
        } catch (TimeoutException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    private boolean connectAndAuthorize() throws IOException {
        CompletableFuture<Boolean> connectedAndAuthorized = openWebsocketConnection();
        try {
            return connectedAndAuthorized.get(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while connecting with websocket");
        } catch (TimeoutException | ExecutionException e) {
            throw new IOException(e);
        }
    }

    private CompletableFuture<String> send(String p, String msg) {
        CompletableFuture<String> future = new CompletableFuture<>();
        LOG.trace("--> {}", msg);
        boolean sent = websocket.send(msg);
        if (!sent) {
            future.completeExceptionally(new IOException("send() returned false"));
        } else {
            responseFuturesByPath.put(p, future);
        }
        return future;
    }

    private void requestAccessToken() throws IOException {
        Request req = new Request.Builder() // @formatter:off
                .url("https://webchat.cam4.com/requestAccess?roomname=" + model.getName())
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(REFERER, Cam4.BASE_URI + '/' + model.getName())
                .header(ORIGIN, Cam4.BASE_URI)
                .header(ACCEPT, "*/*")
                .build(); // @formatter:on
        try (Response response = site.getHttpClient().execute(req)) {
            if (response.isSuccessful()) {
                JSONObject body = new JSONObject(response.body().string());
                if (body.optString("status").equals("success")) {
                    shard = body.getString("shard").replace("https", "wss");
                    token = body.getString("token");
                } else {
                    throw new ModelOfflineException(model);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private JSONObject parseRoomStateResponse(String msg) {
        JSONObject json = new JSONObject(msg);
        JSONObject d = json.getJSONObject("d");
        JSONObject b = d.getJSONObject("b");
        return b.getJSONObject("d");
    }

    private CompletableFuture<Boolean> openWebsocketConnection() {
        CompletableFuture<Boolean> connectedAndAuthorized = new CompletableFuture<>();

        String url = shard + ".ws?v=5";
        LOG.trace("Opening websocket {}", url);
        Request req = new Request.Builder() // @formatter:off
                .url(url)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(REFERER, Cam4.BASE_URI + '/' + model.getName())
                .header(ORIGIN, Cam4.BASE_URI)
                .header(ACCEPT, "*/*")
                .build(); // @formatter:on

        websocket = site.getHttpClient().newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                try {
                    LOG.trace("open: {}", response.body().string());
                } catch (IOException e) {
                    LOG.error("Connection open, but couldn't get the response body", e);
                }
                send("", "{\"t\":\"d\",\"d\":{\"r\":" + (r++) + ",\"a\":\"s\",\"b\":{\"c\":{\"sdk.js.2-3-1\":1}}}}");
                send("", "{\"t\":\"d\",\"d\":{\"r\":" + (r++) + ",\"a\":\"auth\",\"b\":{\"cred\":\"" + token + "\"}}}");
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                LOG.trace("closed: {} {}", code, reason);
                connectedAndAuthorized.complete(false);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                try {
                    if (t instanceof EOFException) {
                        return;
                    }
                    if(response != null) {
                        LOG.error("failure {}: {}", model, response.body().string(), t);
                    } else {
                        LOG.error("failure {}:", model, t);
                    }
                } catch (IOException e) {
                    LOG.error("Connection failure and couldn't get the response body", e);
                } finally {
                    connectedAndAuthorized.completeExceptionally(t);
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                LOG.trace("msgt: {}", text);
                JSONObject response = new JSONObject(text);
                if (response.has("d")) {
                    JSONObject d = response.getJSONObject("d");
                    int responseSequence = d.optInt("r");
                    if (responseSequence == 2) {
                        JSONObject body = d.getJSONObject("b");
                        String status = body.optString("s");
                        connectedAndAuthorized.complete(status.equals("ok"));
                    } else if (responseFuturesBySequence.containsKey(responseSequence)) {
                        JSONObject body = d.getJSONObject("b");
                        String status = body.optString("s");
                        if (!status.equals("ok")) {
                            CompletableFuture<String> future = responseFuturesBySequence.remove(responseSequence);
                            future.completeExceptionally(new IOException(status));
                        }
                    } else if (d.has("b")) {
                        JSONObject body = d.getJSONObject("b");
                        String p = body.optString("p", "-");
                        if (responseFuturesByPath.containsKey(p)) {
                            CompletableFuture<String> future = responseFuturesByPath.remove(p);
                            future.complete(text);
                        }
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
                LOG.trace("msgb: {}", bytes.hex());
            }
        });
        return connectedAndAuthorized;
    }
}
