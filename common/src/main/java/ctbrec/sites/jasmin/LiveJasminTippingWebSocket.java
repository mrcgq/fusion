package ctbrec.sites.jasmin;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.io.HttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public class LiveJasminTippingWebSocket {
    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminTippingWebSocket.class);

    private String applicationId;
    private String sessionId;
    private String jsm2SessionId;
    private String sb_ip;
    private String sb_hash;
    private String relayHost;
    private String streamHost;
    private String clientInstanceId = "01234567890123456789012345678901"; // TODO where to get or generate a random id?
    private WebSocket relay;
    private Throwable exception;

    private HttpClient client;
    private Model model;

    public LiveJasminTippingWebSocket(HttpClient client) {
        this.client = client;
    }

    public void sendTip(Model model, Config config, double amount) throws IOException, InterruptedException {
        this.model = model;
        getPerformerDetails(model.getName());
        LOG.debug("appid: {}", applicationId);
        LOG.debug("sessionid: {}",sessionId);
        LOG.debug("jsm2sessionid: {}",jsm2SessionId);
        LOG.debug("sb_ip: {}",sb_ip);
        LOG.debug("sb_hash:  {}",sb_hash);
        LOG.debug("relay host: {}",relayHost);
        LOG.debug("stream host: {}",streamHost);
        LOG.debug("clientinstanceid {}",clientInstanceId);

        Request request = new Request.Builder()
                .url("https://" + relayHost + "/")
                .header(ORIGIN, LiveJasmin.baseUrl)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ACCEPT, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .build();
        Object monitor = new Object();
        relay = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                LOG.trace("relay open {}", model.getName());
                sendToRelay("{\"event\":\"register\",\"applicationId\":\"" + applicationId
                        + "\",\"connectionData\":{\"jasmin2App\":true,\"isMobileClient\":false,\"platform\":\"desktop\",\"chatID\":\"freechat\","
                        + "\"sessionID\":\"" + sessionId + "\"," + "\"jsm2SessionId\":\"" + jsm2SessionId + "\",\"userType\":\"user\"," + "\"performerId\":\""
                        + model
                        + "\",\"clientRevision\":\"\",\"proxyIP\":\"\",\"playerVer\":\"nanoPlayerVersion: 3.10.3 appCodeName: Mozilla appName: Netscape appVersion: 5.0 (X11) platform: Linux x86_64\",\"livejasminTvmember\":false,\"newApplet\":true,\"livefeedtype\":null,\"gravityCookieId\":\"\",\"passparam\":\"\",\"brandID\":\"jasmin\",\"cobrandId\":\"\",\"subbrand\":\"livejasmin\",\"siteName\":\"LiveJasmin\",\"siteUrl\":\""+LiveJasmin.baseUrl+"\","
                        + "\"clientInstanceId\":\"" + clientInstanceId + "\",\"armaVersion\":\"34.10.0\",\"isPassive\":false}}");
                response.close();
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                LOG.trace("relay <-- {} T{}", model.getName(), text);
                JSONObject event = new JSONObject(text);
                if (event.optString("event").equals("accept")) {
                    GlobalThreadPool.submit(() -> {
                        sendToRelay("{\"event\":\"connectSharedObject\",\"name\":\"data/chat_so\"}");
                    });
                } else if(event.optString("event").equals("call")) {
                    String func = event.optString("funcName");
                    if (func.equals("setName")) {
                        LOG.debug("Entered chat -> Sending tip of {}", amount);
                        sendToRelay("{\"event\":\"call\",\"funcName\":\"sendSurprise\",\"data\":["+amount+",\"SurpriseGirlFlower\"]}");
                    } else if (func.equals("startSurprise")) {
                        // {"event":"call","funcName":"startSurprise","userId":"xyz_hash_gibberish","data":[{"memberid":"userxyz","amount":1,"tipName":"SurpriseGirlFlower","err_desc":"OK","err_text":"OK"}]}
                        JSONArray dataArray = event.getJSONArray("data");
                        JSONObject data = dataArray.getJSONObject(0);
                        String errText = data.optString("err_text");
                        String errDesc = data.optString("err_desc");
                        LOG.debug("Tip response {} - {}", errText, errDesc);
                        if(!errText.equalsIgnoreCase("OK")) {
                            exception = new IOException(errText + " - " + errDesc);
                        }
                        synchronized (monitor) {
                            monitor.notify();
                        }
                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                LOG.trace("relay <-- {} B{}", model.getName(), bytes.toString());
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                LOG.trace("relay closed {} {} {}", code, reason, model.getName());
                exception = new IOException("Socket closed by server - " + code + " " + reason);
                synchronized (monitor) {
                    monitor.notify();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                exception = t;
                synchronized (monitor) {
                    monitor.notify();
                }
            }
        });
        synchronized (monitor) {
            monitor.wait();
        }
        if(exception != null) {
            throw new IOException(exception);
        }
    }

    private void sendToRelay(String msg) {
        LOG.trace("relay --> {} {}", model.getName(), msg);
        relay.send(msg);
    }

    protected void getPerformerDetails(String name) throws IOException {
        String url = "https://m." + LiveJasmin.baseDomain + "/en/chat-html5/" + name;
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, "Mozilla/5.0 (iPhone; CPU OS 10_14 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/11.1.1 Mobile/14E304 Safari/605.1.15")
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, LiveJasmin.baseUrl)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = client.execute(req)) {
            if (response.isSuccessful()) {
                String body = response.body().string();
                JSONObject json = new JSONObject(body);
                if (json.optBoolean("success")) {
                    JSONObject data = json.getJSONObject("data");
                    JSONObject config = data.getJSONObject("config");
                    JSONObject armageddonConfig = config.getJSONObject("armageddonConfig");
                    JSONObject chatRoom = config.getJSONObject("chatRoom");
                    sessionId = armageddonConfig.getString("sessionid");
                    jsm2SessionId = armageddonConfig.getString("jsm2session");
                    sb_hash = chatRoom.getString("sb_hash");
                    sb_ip = chatRoom.getString("sb_ip");
                    applicationId = "memberChat/jasmin" + name + sb_hash;
                    relayHost = "dss-relay-" + sb_ip.replace('.', '-') + ".dditscdn.com";
                    streamHost = "dss-live-" + sb_ip.replace('.', '-') + ".dditscdn.com";
                } else {
                    throw new IOException("Response was not successful: " + body);
                }
            } else {
                throw new IOException(response.code() + " - " + response.message());
            }
        }
    }
}
