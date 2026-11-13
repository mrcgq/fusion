package ctbrec.sites.jasmin;

import ctbrec.Config;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.Site;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static ctbrec.io.HttpConstants.USER_AGENT;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class LiveJasminStreamRegistration {

    private static final String KEY_EVENT = "event";
    private static final String KEY_FUNC_NAME = "funcName";

    private final Site site;
    private final LiveJasminModelInfo modelInfo;
    private final CyclicBarrier barrier = new CyclicBarrier(2);

    private int streamCount = 0;
    private WebSocket webSocket;

    public LiveJasminStreamRegistration(Site site, LiveJasminModelInfo modelInfo) {
        this.site = site;
        this.modelInfo = modelInfo;
    }

    List<StreamSource> getStreamSources() {
        var streamSources = new LinkedList<LiveJasminStreamSource>();
        try {
            Request webSocketRequest = new Request.Builder()
                    .url(modelInfo.getWebsocketUrl())
                    .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgentMobile)
                    .build();
            log.debug("Websocket: {}", modelInfo.getWebsocketUrl());
            webSocket = site.getHttpClient().newWebSocket(webSocketRequest, new WebSocketListener() {
                @Override
                public void onOpen(@NotNull WebSocket webSocket, @NotNull Response response) {
                    Thread.currentThread().setName("Stream registration for " + modelInfo.getPerformerId());
                    log.debug("onOpen");
                    JSONObject register = new JSONObject()
                            .put(KEY_EVENT, "register")
                            .put("applicationId", "memberChat/jasmin" + modelInfo.getPerformerId() + modelInfo.getSbHash())
                            .put("connectionData", new JSONObject()
                                    .put("sessionID", modelInfo.getSessionId())
                                    .put("jasmin2App", true)
                                    .put("isMobileClient", false)
                                    .put("platform", "desktop")
                                    .put("chatID", "freechat")
                                    .put("jsm2SessionId", modelInfo.getJsm2session())
                                    .put("userType", "user")
                                    .put("performerId", modelInfo.getPerformerId())
                                    .put("clientRevision", "")
                                    .put("livejasminTvmember", false)
                                    .put("newApplet", true)
                                    .put("livefeedtype", JSONObject.NULL)
                                    .put("gravityCookieId", "")
                                    .put("passparam", "")
                                    .put("brandID", "jasmin")
                                    .put("cobrandId", "livejasmin")
                                    .put("subbrand", "livejasmin")
                                    .put("siteName", "LiveJasmin")
                                    .put("siteUrl", "https://www.livejasmin.com")
                                    .put("clientInstanceId", modelInfo.getClientInstanceId())
                                    .put("armaVersion", "38.32.1-LIVEJASMIN-44016-1")
                                    .put("isPassive", false)
                                    .put("peekPatternJsm2", true)
                                    .put("chatHistoryRequired", true)
                            );
                    log.trace("Stream registration\n{}", register.toString(2));
                    send(register.toString());
                    send(new JSONObject().put(KEY_EVENT, "ping").toString());
                    send(new JSONObject()
                            .put(KEY_EVENT, "call")
                            .put(KEY_FUNC_NAME, "makeActive")
                            .put("data", new JSONArray())
                            .toString());
                    send(new JSONObject()
                            .put(KEY_EVENT, "call")
                            .put(KEY_FUNC_NAME, "setVideo")
                            .put("data", new JSONArray()
                                    .put(JSONObject.NULL)
                                    .put(false)
                                    .put(true)
                                    .put(modelInfo.getJsm2session())
                            )
                            .toString());
                    send(new JSONObject()
                            .put(KEY_EVENT, "connectSharedObject")
                            .put("name", "data/chat_so")
                            .toString());
                }

                @Override
                public void onFailure(@NotNull WebSocket webSocket, @NotNull Throwable t, @Nullable Response response) {
                    log.error("onFailure", t);
                    awaitBarrier();
                    webSocket.close(1000, "");
                }

                @Override
                public void onMessage(@NotNull WebSocket webSocket, @NotNull String text) {
                    log.trace("<   {}", text);
                    JSONObject message = new JSONObject(text);
                    if (message.opt(KEY_EVENT).equals("pong")) {
                        new Thread(() -> {
                            try {
                                Thread.sleep(message.optInt("nextPing"));
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                            send(new JSONObject().put(KEY_EVENT, "ping").toString());
                        }).start();
                    } else if (message.optString(KEY_EVENT).equals("updateSharedObject") && message.optString("name").equals("data/chat_so")) {
                        log.trace(message.toString(2));
                        JSONArray list = message.getJSONArray("list");
                        for (int i = 0; i < list.length(); i++) {
                            JSONObject attribute = list.getJSONObject(i);
                            if (attribute.optString("name").equals("streamList")) {
                                JSONObject value = attribute.getJSONObject("newValue");
                                JSONObject patterns = value.getJSONObject("patterns");
                                String freePattern = patterns.getString("free");
                                JSONArray streams = value.getJSONArray("streams");
                                for (int j = 0; j < streams.length(); j++) {
                                    JSONObject stream = streams.getJSONObject(j);
                                    addStreamSource(streamSources, freePattern, stream);
                                }
                                Collections.sort(streamSources);
                                Collections.reverse(streamSources);
                                for (LiveJasminStreamSource src : streamSources) {
                                    JSONObject getVideoData = new JSONObject()
                                            .put(KEY_EVENT, "call")
                                            .put(KEY_FUNC_NAME, "getVideoData")
                                            .put("data", new JSONArray()
                                                    .put(new JSONObject()
                                                            .put("protocols", new JSONArray()
                                                                    .put("h5live")
                                                            )
                                                            .put("streamId", src.getStreamId())
                                                            .put("correlationId", UUID.randomUUID().toString().replace("-", "").substring(0, 16))
                                                    )
                                            );
                                    streamCount++;
                                    send(getVideoData.toString());
                                }
                            }
                        }
                    } else if (message.optString(KEY_FUNC_NAME).equals("setVideoData")) {
                        JSONObject data = message.getJSONArray("data").getJSONArray(0).getJSONObject(0);
                        String streamId = data.getString("streamId");
                        String wssUrl = data.getJSONObject("protocol").getJSONObject("h5live").getString("wssUrl");
                        streamSources.stream().filter(src -> Objects.equals(src.getStreamId(), streamId)).findAny().ifPresent(src -> src.setMediaPlaylistUrl(wssUrl));
                        if (--streamCount == 0) {
                            awaitBarrier();
                        }
                    } else if (!message.optString(KEY_FUNC_NAME).equals("chatHistory")) {
                        log.trace("onMessageT {}", new JSONObject(text).toString(2));
                    }
                }

                @Override
                public void onMessage(@NotNull WebSocket webSocket, @NotNull ByteString bytes) {
                    log.trace("onMessageB");
                    super.onMessage(webSocket, bytes);
                }

                @Override
                public void onClosed(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                    log.trace("onClosed {} {}", code, reason);
                    super.onClosed(webSocket, code, reason);
                }

                @Override
                public void onClosing(@NotNull WebSocket webSocket, int code, @NotNull String reason) {
                    log.trace("onClosing {} {}", code, reason);
                    awaitBarrier();
                }

                private void send(String msg) {
                    log.debug("  > {}", msg);
                    webSocket.send(msg);
                }
            });

            log.debug("Waiting for websocket to return");
            awaitBarrier();
            log.debug("Websocket is done. Stream sources {}", streamSources);
        } catch (Exception e) {
            log.error("Couldn't determine stream sources", e);
        }
        return streamSources.stream().map(StreamSource.class::cast).collect(Collectors.toList()); // NOSONAR
    }

    private void addStreamSource(LinkedList<LiveJasminStreamSource> streamSources, String pattern, JSONObject stream) {
        int w = stream.getInt("width");
        int h = stream.getInt("height");
        int bitrate = stream.getInt("bitrate") * 1024;
        String name = stream.getString("name");
        String streamName = pattern.replace("{$streamname}", name);
        String streamId = stream.getString("streamId");

        String rtmpUrl = "rtmp://{ip}/memberChat/jasmin{modelName}{sb_hash}?sessionId-{sessionId}|clientInstanceId-{clientInstanceId}"
                .replace("{ip}", modelInfo.getSbIp())
                .replace("{modelName}", modelInfo.getPerformerId())
                .replace("{sb_hash}", modelInfo.getSbHash())
                .replace("{sessionId}", modelInfo.getSessionId())
                .replace("{clientInstanceId}", modelInfo.getClientInstanceId());

        String hlsUrl = "https://dss-hls-{ipWithDashes}.dditscdn.com/h5live/http/playlist.m3u8?url={rtmpUrl}&stream={streamName}"
                .replace("{ipWithDashes}", modelInfo.getSbIp().replace('.', '-'))
                .replace("{rtmpUrl}", URLEncoder.encode(rtmpUrl, UTF_8))
                .replace("{streamName}", URLEncoder.encode(streamName, UTF_8));

        LiveJasminStreamSource streamSource = new LiveJasminStreamSource();
        streamSource.setMediaPlaylistUrl(hlsUrl);
        streamSource.setWidth(w);
        streamSource.setHeight(h);
        streamSource.setBandwidth(bitrate);
        streamSource.setRtmpUrl(rtmpUrl);
        streamSource.setStreamName(streamName);
        streamSource.setStreamId(streamId);
        streamSource.setStreamRegistration(this);
        streamSources.add(streamSource);
    }

    private void awaitBarrier() {
        try {
            barrier.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error(e.getLocalizedMessage(), e);
        } catch (TimeoutException | BrokenBarrierException e) {
            log.error(e.getLocalizedMessage(), e);
        }
    }

    void close() {
        webSocket.close(1000, "");
    }
}
