package ctbrec.sites.mfc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpException;
import ctbrec.io.json.ObjectMapperFactory;
import okhttp3.*;
import okio.ByteString;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static ctbrec.io.HttpConstants.*;
import static ctbrec.sites.mfc.MessageTypes.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class MyFreeCamsClient {

    private static final String HTTPS = "https://";

    private static final Logger LOG = LoggerFactory.getLogger(MyFreeCamsClient.class);

    private static final ObjectMapper objectMapper = ObjectMapperFactory.getMapper();

    private static MyFreeCamsClient instance;
    private MyFreeCams mfc;
    private WebSocket ws;
    private Thread keepAlive;
    private volatile boolean running = false;

    private final Cache<Integer, SessionState> sessionStates = CacheBuilder.newBuilder().maximumSize(4000).build();
    private final Cache<Integer, MyFreeCamsModel> models = CacheBuilder.newBuilder().maximumSize(4000).build();
    private final Lock lock = new ReentrantLock();
    private ServerConfig serverConfig;
    @SuppressWarnings("unused")
    private String tkx;
    private Integer cxid;
    private int[] ctx;
    private String ctxenc;
    private int sessionId;
    private long heartBeat;
    private volatile boolean connecting = false;
    private static int messageId = 31415; // starting with 31415 just for fun
    private final Map<Integer, Consumer<Message>> responseHandlers = new HashMap<>();
    private final Random rng = new Random();

    private final Queue<String> receivedTextHistory = new LinkedList<>();

    private MyFreeCamsClient() {
    }

    public static synchronized MyFreeCamsClient getInstance() {
        if (instance == null) {
            instance = new MyFreeCamsClient();
        }
        return instance;
    }

    public void setSite(MyFreeCams mfc) {
        this.mfc = mfc;
    }

    public void start() throws IOException {
        requestLandingPage(); // to get some cookies
        running = true;
        serverConfig = new ServerConfig(mfc);
        List<String> websocketServers = new ArrayList<>(serverConfig.wsServers.size());
        for (Entry<String, String> entry : serverConfig.wsServers.entrySet()) {
            if (entry.getValue().equals("rfc6455")) {
                websocketServers.add(entry.getKey());
            }
        }

        String server = websocketServers.get(rng.nextInt(websocketServers.size() - 1));
        String wsUrl = "wss://" + server + ".myfreecams.com/fcsl";
        LOG.debug("Connecting to random websocket server {}", wsUrl);

        Thread watchDog = new Thread(() -> {
            while (running) {
                if (ws == null && !connecting) {
                    LOG.info("Websocket is null. Starting a new connection");
                    Request req = new Request.Builder()
                            .url(wsUrl)
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header(ORIGIN, MyFreeCams.baseUrl)
                            .build();
                    ws = createWebSocket(req);
                }

                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("WatchDog couldn't sleep", e);
                    stop();
                    running = false;
                }
            }
        });
        watchDog.setDaemon(true);
        watchDog.setName("MFC WebSocket WatchDog");
        watchDog.setPriority(Thread.MIN_PRIORITY);
        watchDog.start();
    }

    private void requestLandingPage() throws IOException {
        Request req = new Request.Builder()
                .url(MyFreeCams.baseUrl)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(CONNECTION, KEEP_ALIVE)
                .build();
        try (Response resp = mfc.getHttpClient().execute(req)) {
            if (!resp.isSuccessful()) {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    public void stop() {
        running = false;
        ws.close(1000, "Good Bye"); // terminate normally (1000)
    }

    public List<MyFreeCamsModel> getModels() {
        lock.lock();
        try {
            LOG.trace("Models: {}", models.size());
            return new ArrayList<>(this.models.asMap().values());
        } finally {
            lock.unlock();
        }
    }

    private WebSocket createWebSocket(Request req) {
        connecting = true;
        WebSocket websocket = mfc.getHttpClient().newWebSocket(req, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                super.onOpen(webSocket, response);
                try {
                    connecting = false;
                    sessionStates.invalidateAll();
                    models.invalidateAll();
                    // WebSocketListener's response body is stripped, so we cannot call .string() on it (remove the line maybe?)
                    var body = response.body();
                    LOG.trace("open: [{}]", body.contentLength() != 0 ? body.string() : "");
                    webSocket.send("fcsws_20180422\n");
                    login(webSocket);
                    heartBeat = System.currentTimeMillis();
                    startKeepAlive(webSocket);
                } catch (IOException e) {
                    LOG.error("Error while processing onOpen event", e);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                super.onClosed(webSocket, code, reason);
                connecting = false;
                LOG.info("MFC websocket closed: {} {}", code, reason);
                MyFreeCamsClient.this.ws = null;
                if (!running) {
                    mfc.getHttpClient().shutdown();
                }
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                super.onFailure(webSocket, t, response);
                connecting = false;
                if (response != null) {
                    int code = response.code();
                    String message = response.message();
                    LOG.error("MFC websocket failure: {} {}", code, message, t);
                    response.close();
                } else {
                    LOG.error("MFC websocket failure", t);
                }
                MyFreeCamsClient.this.ws = null;
            }

            private final StringBuilder msgBuffer = new StringBuilder();

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                super.onMessage(webSocket, text);
                heartBeat = System.currentTimeMillis();
                receivedTextHistory.add(text);
                while (receivedTextHistory.size() > 100) {
                    receivedTextHistory.poll();
                }
                msgBuffer.append(text);
                Message message;
                try {
                    while ((message = parseMessage(msgBuffer)) != null) {
                        switch (message.getType()) {
                            case NULL:
                                LOG.trace("NULL websocket still alive");
                                break;
                            case LOGIN:
                                LOG.debug("LOGIN: {}", message);
                                sessionId = message.getReceiver();
                                LOG.debug("Session ID {}", sessionId);
                                break;
                            case DETAILS:
                            case ROOMHELPER:
                            case ADDFRIEND:
                            case ADDIGNORE:
                            case CMESG:
                            case PMESG:
                            case TXPROFILE:
                            case MYCAMSTATE:
                            case MYWEBCAM:
                            case JOINCHAN:
                            case SESSIONSTATE:
                                if (!message.getMessage().isEmpty()) {
                                    try {
                                        SessionState sessionState = objectMapper.readValue(message.getMessage(), SessionState.class);
                                        updateSessionState(sessionState);
                                    } catch (IOException e) {
                                        LOG.error("Couldn't parse session state message {}", message, e);
                                    }
                                }
                                break;
                            case USERNAMELOOKUP:
                                // LOG.debug("{}", message.getType());
                                // LOG.debug("{}", message.getSender());
                                // LOG.debug("{}", message.getReceiver());
                                // LOG.debug("{}", message.getArg1());
                                // LOG.debug("{}", message.getArg2());
                                // LOG.debug("{}", message.getMessage());
                                Consumer<Message> responseHandler = responseHandlers.remove(message.getArg1());
                                if (responseHandler != null) {
                                    responseHandler.accept(message);
                                }
                                break;
                            case TAGS:
                                JSONObject json = new JSONObject(message.getMessage());
                                String[] names = JSONObject.getNames(json);
                                Integer uid = Integer.parseInt(names[0]);
                                SessionState sessionState = sessionStates.getIfPresent(uid);
                                if (sessionState != null) {
                                    JSONArray tags = json.getJSONArray(names[0]);
                                    for (Object obj : tags) {
                                        sessionState.getM().getTags().add((String) obj);
                                    }
                                }
                                break;
                            case EXTDATA:
                                if (message.getArg1() == MessageTypes.LOGIN) {
                                    // noop
                                } else if (message.getArg1() == MessageTypes.MANAGELIST) {
                                    requestExtData(message.getMessage());
                                } else {
                                    LOG.debug("EXTDATA: {}", message);
                                }
                                break;
                            case ROOMDATA:
                                LOG.debug("ROOMDATA: {}", message);
                                break;
                            case UEOPT:
                                LOG.trace("UEOPT: {}", message);
                                break;
                            case SLAVEVSHARE:
                                // LOG.debug("SLAVEVSHARE {}", message);
                                // LOG.debug("SLAVEVSHARE MSG [{}]", message.getMessage());
                                break;
                            case TKX:
                                json = new JSONObject(message.getMessage());
                                tkx = json.getString("tkx");
                                cxid = json.getInt("cxid");
                                ctxenc = URLDecoder.decode(json.getString("ctxenc"), UTF_8);
                                JSONArray ctxArray = json.getJSONArray("ctx");
                                ctx = new int[ctxArray.length()];
                                for (int i = 0; i < ctxArray.length(); i++) {
                                    ctx[i] = ctxArray.getInt(i);
                                }
                                break;
                            default:
                                LOG.trace("Unknown message {}", message);
                                break;
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Exception occured while processing websocket message {}", msgBuffer, e);
                    ws.close(1000, "");
                }
            }

            private void login(WebSocket webSocket) throws IOException {
                String username = Config.getInstance().getSettings().mfcUsername;
                if (StringUtil.isNotBlank(username)) {
                    boolean login = mfc.getHttpClient().login();
                    if (login) {
                        Cookie passcode = mfc.getHttpClient().getCookie("passcode");
                        webSocket.send("1 0 0 20071025 0 1/" + username + ":" + passcode.value() + "\n");
                    } else {
                        LOG.error("Login failed. Logging in as guest");
                        webSocket.send("1 0 0 20071025 0 1/guest:guest\n");
                    }
                } else {
                    webSocket.send("1 0 0 20071025 0 1/guest:guest\n");
                }
            }

            private void requestExtData(String message) {
                try {
                    JSONObject json = new JSONObject(message);
                    long respkey = json.getInt("respkey");
                    long opts = json.getInt("opts");
                    long serv = json.getInt("serv");
                    long type = json.getInt("type");
                    String base = mfc.getBaseUrl() + "/php/FcwExtResp.php";
                    String url = base + "?respkey=" + respkey + "&opts=" + opts + "&serv=" + serv + "&type=" + type;
                    Request req = new Request.Builder()
                            .url(url)
                            .header(ACCEPT, "*/*")
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header(CONNECTION, KEEP_ALIVE)
                            .build();
                    LOG.trace("Requesting EXTDATA {}", url);
                    try (Response resp = mfc.getHttpClient().execute(req)) {
                        if (resp.isSuccessful()) {
                            parseExtDataSessionStates(Objects.requireNonNull(resp.body(), "HTTP response is null").string());
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Couldn't request EXTDATA", e);
                }
            }

            private void parseExtDataSessionStates(String json) {
                JSONObject object = new JSONObject(json);
                if (object.has("type") && object.getInt("type") == 21) {
                    JSONArray outer = object.getJSONArray("rdata");
                    LOG.debug("{} models", outer.length());
                    for (int i = 1; i < outer.length(); i++) {
                        JSONArray inner = outer.getJSONArray(i);
                        try {
                            SessionState state = new SessionState();
                            int idx = 0;
                            state.setNm(inner.getString(idx++));
                            state.setSid(inner.getInt(idx++));
                            state.setUid(inner.getInt(idx++));
                            state.setVs(inner.getInt(idx++));
                            state.setPid(inner.getInt(idx++));
                            state.setLv(inner.getInt(idx++));
                            state.setU(new User());
                            state.getU().setCamserv(inner.getInt(idx++));
                            state.getU().setPhase(inner.getString(idx++));
                            state.getU().setChatColor(inner.getString(idx++));
                            state.getU().setChatFont(inner.getInt(idx++));
                            state.getU().setChatOpt(inner.getInt(idx++));
                            state.getU().setCreation(inner.getInt(idx++));
                            state.getU().setAvatar(inner.getInt(idx++));
                            state.getU().setProfile(inner.getInt(idx++));
                            state.getU().setPhotos(inner.getInt(idx++));
                            state.getU().setBlurb(inner.getString(idx++));
                            state.setM(new Model());
                            state.getM().setNewModel(inner.getInt(idx++));
                            state.getM().setMissmfc(inner.getInt(idx++));
                            state.getM().setCamscore(inner.getDouble(idx++));
                            state.getM().setContinent(inner.getString(idx++));
                            state.getM().setFlags(inner.getInt(idx++));
                            state.getM().setRank(inner.getInt(idx++));
                            state.getM().setRc(inner.getInt(idx++));
                            state.getM().setTopic(inner.getString(idx++));
                            state.getM().setHidecs(inner.getInt(idx) == 1);
                            updateSessionState(state);
                        } catch (Exception e) {
                            LOG.warn("Couldn't parse session state {}", inner);
                        }
                    }
                } else if (object.has("type") && object.getInt("type") == 20) {
                    JSONObject outer = object.getJSONObject("rdata");
                    for (String uidString : outer.keySet()) {
                        try {
                            int uid = Integer.parseInt(uidString);
                            MyFreeCamsModel model = getModel(uid);
                            if (model != null) {
                                model.getTags().clear();
                                JSONArray jsonTags = outer.getJSONArray(uidString);
                                jsonTags.forEach(tag -> model.getTags().add((String) tag));
                            }
                        } catch (Exception e) {
                            // fail silently
                        }

                    }
                }
            }

            @Override
            public void onMessage(WebSocket webSocket, ByteString bytes) {
                super.onMessage(webSocket, bytes);
                LOG.debug("msgb: {}", bytes.hex());
            }
        });
        return websocket;
    }

    private void updateSessionState(SessionState newState) {
        if (newState.getUid() <= 0) {
            return;
        }
        SessionState storedState = sessionStates.getIfPresent(newState.getUid());
        if (storedState != null) {
            storedState.merge(newState);
            updateModel(storedState);
        } else {
            lock.lock();
            try {
                sessionStates.put(newState.getUid(), newState);
                updateModel(newState);
            } finally {
                lock.unlock();
            }
        }
    }

    private void updateModel(SessionState state) {
        // essential data not yet available
        if (state.getNm() == null || state.getM() == null || state.getU() == null || state.getU().getCamserv() == null
                || state.getU().getCamserv() == 0) {
            return;
        }

        // tokens not yet available
        if (ctxenc == null) {
            return;
        }

        // uid not set, we can't identify this model
        if (state.getUid() == null || state.getUid() <= 0) {
            return;
        }

        MyFreeCamsModel model = models.getIfPresent(state.getUid());
        if (model == null) {
            model = mfc.createModel(state.getNm());
            model.setUid(state.getUid());
            models.put(state.getUid(), model);
        }
        model.update(state, getStreamUrl(state));
    }

    private Message parseMessage(StringBuilder msgBuffer) {
        int packetLengthBytes = 6;
        if (msgBuffer.length() < packetLengthBytes) {
            // packet size not transmitted completely
            return null;
        } else {
            try {
                int packetLength = Integer.parseInt(msgBuffer.substring(0, packetLengthBytes));
                if (packetLength > msgBuffer.length() - packetLengthBytes) {
                    // packet not complete
                    return null;
                } else {
                    LOG.trace("<-- {}", msgBuffer);
                    msgBuffer.delete(0, packetLengthBytes);
                    StringBuilder rawMessage = new StringBuilder(msgBuffer.substring(0, packetLength));
                    int type = parseNextInt(rawMessage);
                    int sender = parseNextInt(rawMessage);
                    int receiver = parseNextInt(rawMessage);
                    int arg1 = parseNextInt(rawMessage);
                    int arg2 = parseNextInt(rawMessage);
                    Message message = new Message(type, sender, receiver, arg1, arg2, URLDecoder.decode(rawMessage.toString(), UTF_8));
                    msgBuffer.delete(0, packetLength);
                    return message;
                }
            } catch (Exception e) {
                LOG.error("StringBuilder contains invalid data {}", msgBuffer, e);
                String logfile = "mfc_messages.log";
                try (FileOutputStream fout = new FileOutputStream(logfile)) {
                    for (String string : receivedTextHistory) {
                        fout.write(string.getBytes());
                        fout.write(10);
                    }
                } catch (Exception e1) {
                    LOG.error("Couldn't write mfc message history to {}", logfile, e1);
                }
                msgBuffer.setLength(0);
                return null;
            }
        }
    }

    private int parseNextInt(StringBuilder s) {
        int nextSpace = s.indexOf(" ");
        int i = Integer.parseInt(s.substring(0, nextSpace));
        s.delete(0, nextSpace + 1);
        return i;
    }

    protected boolean follow(int uid) {
        if (ws != null) {
            return ws.send(ADDFRIENDREQ + " " + sessionId + " 0 " + uid + " 1\n");
        } else {
            return false;
        }
    }

    protected boolean unfollow(int uid) {
        if (ws != null) {
            return ws.send(ADDFRIENDREQ + " " + sessionId + " 0 " + uid + " 2\n");
        } else {
            return false;
        }
    }

    protected String getWebrtcAuthCommand(SessionState state) {
        JSONObject streamInfo = new JSONObject();
        int userChannel = 100000000 + state.getUid();
        String phase = Optional.ofNullable(state.getU()).map(User::getPhase).orElse("z");
        String phasePrefix = phase.equals("z") ? "" : '_' + phase;
        String streamName = "mfc" + phasePrefix + '_' + userChannel + ".f4v";
        streamInfo.put("applicationName", "NxServer");
        streamInfo.put("streamName", streamName);
        streamInfo.put("sessionId", "[empty]");
        JSONObject userData = new JSONObject();
        userData.put("sessionId", sessionId);
        userData.put("password", ""); // tkx or "" ?!?
        userData.put("roomId", userChannel);
        userData.put("modelId", state.getUid());
        if (isBroadcasterOnOBS(state)) {
            JSONArray array = new JSONArray();
            Arrays.stream(ctx).forEach(array::put);
            userData.put("vidctx", Base64.getEncoder().encodeToString(array.toString().getBytes(UTF_8)));
            userData.put("cxid", cxid);
        }
        userData.put("mode", "DOWNLOAD");
        JSONObject authCommand = new JSONObject();
        authCommand.put("command", "auth");
        authCommand.put("streamInfo", streamInfo);
        authCommand.put("userData", userData);
        LOG.info("auth command {}", authCommand.toString(2));
        return streamInfo.toString();
    }

    private boolean isBroadcasterOnOBS(SessionState state) {
        String phase = Optional.ofNullable(state.getU()).map(User::getPhase).orElse("z");
        return !phase.equals("z");
    }

    private void startKeepAlive(WebSocket ws) {
        if (keepAlive != null) {
            keepAlive.interrupt();
        }
        keepAlive = new Thread(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    if (!connecting) {
                        LOG.trace("--> NULL to keep the connection alive");
                        ws.send("0 0 0 0 0 -\n");

                        long millisSinceLastMessage = System.currentTimeMillis() - heartBeat;
                        if (millisSinceLastMessage > TimeUnit.MINUTES.toMillis(2)) {
                            LOG.info("No message since 2 mins. Restarting websocket");
                            ws.close(1000, "");
                            MyFreeCamsClient.this.ws = null;
                        }
                    } else {
                        // we are establishing a new connection at the moment, no need to do anything
                    }

                    TimeUnit.SECONDS.sleep(15);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.warn("Websocket watchdog has been interrupted");
                }
            }
        });
        keepAlive.setName("KeepAlive");
        keepAlive.setDaemon(true);
        keepAlive.start();
    }

    public void update(MyFreeCamsModel model) {
        lock.lock();
        try {
            for (SessionState state : sessionStates.asMap().values()) {
                Optional<String> nm = Optional.ofNullable(state.getNm());
                Optional<String> name = Optional.ofNullable(model.getName());
                if (nm.isEmpty() || name.isEmpty()) {
                    continue;
                }

                if (Objects.equals(nm.get().toLowerCase(), name.get().toLowerCase()) || Objects.equals(model.getUid(), state.getUid()) && state.getUid() > 0) {
                    model.update(state, getStreamUrl(state));
                    return;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public String getStreamUrl(SessionState state) {
        int userChannel = 100000000 + state.getUid();
        String phase = Optional.of(state).map(SessionState::getU).map(User::getPhase).orElse("z");
        String phasePrefix = phase.equals("z") ? "" : '_' + phase;
        String server = "video" + getCamServ(state).replaceAll("^\\D+", "");
        String nonce = Double.toString(Math.random());
        String streamUrl = HTTPS + server + ".myfreecams.com/NxServer/ngrp:mfc" + phasePrefix + '_' + userChannel + ".f4v_mobile/playlist.m3u8?nc=" + nonce + "&v=1.96";
        return streamUrl;
    }

    private String getCamServ(SessionState state) {
        int camserv = Optional.ofNullable(state.getU()).map(User::getCamserv).orElse(-1);
        String camservString = Integer.toString(camserv);
        if (serverConfig.isOnWzObsVideoServer(state)) {
            camservString = serverConfig.wzobsServers.get(camservString);
        } else if (serverConfig.isOnObsServer(state)) {
            camservString = serverConfig.ngVideoServers.get(camservString);
        } else if (serverConfig.isOnHtml5VideoServer(state)) {
            camservString = serverConfig.h5Servers.get(camservString);
        } else if (camserv > 500) {
            if (camserv >= 3000) {
                camserv -= 1000;
            } else {
                camserv -= 500;
            }
            camservString = Integer.toString(camserv);
        }
        return camservString;
    }

    @SuppressWarnings("unused")
    private boolean isBroadcasterOnWebRTC(SessionState state) {
        return (Optional.ofNullable(state).map(SessionState::getM).map(Model::getFlags).orElse(0) & 524288) == 524288;
    }

    public MyFreeCamsModel getModel(int uid) {
        return models.getIfPresent(uid);
    }

    public SessionState getSessionState(ctbrec.Model model) {
        if (model instanceof MyFreeCamsModel mfcModel) {
            for (SessionState state : sessionStates.asMap().values()) {
                if (mfcModel.getUid() > 0 && state.getUid() != null && state.getUid() > 0 && mfcModel.getUid() == state.getUid()) {
                    return state;
                }
            }
        }
        return null;
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }

    public List<ctbrec.Model> search(String q) throws InterruptedException {
        List<ctbrec.Model> result = new ArrayList<>();
        if (ws != null) {
            LOG.trace("Sending USERNAMELOOKUP for {}", q);
            Object monitor = new Object();
            int msgId = messageId++;
            AtomicBoolean searchDone = new AtomicBoolean(false);
            responseHandlers.put(msgId, msg -> {
                try {
                    LOG.trace("Search result: {}", msg);
                    if (StringUtil.isNotBlank(msg.getMessage()) && !Objects.equals(msg.getMessage(), q)) {
                        JSONObject json = new JSONObject(msg.getMessage());
                        try {
                            SessionState sessionState = Objects.requireNonNull(objectMapper.readValue(msg.getMessage(), SessionState.class));
                            updateSessionState(sessionState);
                        } catch (Exception e) {
                            LOG.error("Couldn't parse session state message {}", msg, e);
                        }

                        String name = json.getString("nm");
                        MyFreeCamsModel model = mfc.createModel(name);
                        model.setUid(json.getInt("uid"));
                        model.setMfcState(State.of(json.getInt("vs")));
                        String uid = Integer.toString(model.getUid());
                        String uidStart = uid.substring(0, 3);
                        String previewUrl = "https://img.mfcimg.com/photos2/" + uidStart + '/' + uid + "/avatar.90x90.jpg";
                        model.setPreview(previewUrl);
                        result.add(model);
                    }
                } finally {
                    searchDone.setPlain(true);
                    synchronized (monitor) {
                        monitor.notifyAll();
                    }
                }
            });
            ws.send("10 " + sessionId + " 0 " + msgId + " 0 " + q + "\n");
            int waitInMillis = 1000;
            int iterations = 0;
            while (iterations < 5 && !searchDone.get()) {
                synchronized (monitor) {
                    monitor.wait(waitInMillis);
                    iterations++;
                }
            }

            for (MyFreeCamsModel model : models.asMap().values()) {
                if (StringUtil.isNotBlank(model.getName()) && model.getName().toLowerCase().contains(q.toLowerCase())) {
                    result.add(model);
                }
            }
        }
        return result;
    }

    /**
     * Tries to look up the sessionId of a model
     */
    public String getSessionId(String modelName) throws InterruptedException {
        if (ws == null) {
            return "";
        }

        LOG.trace("Sending USERNAMELOOKUP for {}", modelName);
        int msgId = messageId++;
        Object monitor = new Object();

        List<String> resultHolder = new ArrayList<>();
        responseHandlers.put(msgId, msg -> {
            LOG.trace("Search result: {}", msg);
            if (StringUtil.isNotBlank(msg.getMessage()) && !Objects.equals(msg.getMessage(), modelName)) {
                JSONObject json = new JSONObject(msg.getMessage());
                resultHolder.add(Integer.toString(json.optInt("sid")));
            }
            synchronized (monitor) {
                monitor.notifyAll();
            }
        });
        ws.send("10 " + sessionId + " 0 " + msgId + " 0 " + modelName + "\n");
        synchronized (monitor) {
            monitor.wait();
        }

        return resultHolder.isEmpty() ? "" : resultHolder.get(0);
    }

    public Collection<SessionState> getSessionStates() {
        return Collections.unmodifiableCollection(sessionStates.asMap().values());
    }

    public void joinChannel(MyFreeCamsModel model) {
        SessionState state = getSessionState(model);
        int userChannel = 100000000 + state.getUid();
        LOG.debug("Joining chat channel for model {}", model.getDisplayName());
        try {
            search(model.getName());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        ws.send(MessageTypes.ROOMDATA + " " + sessionId + " 0 1 0\n");
        ws.send(MessageTypes.UEOPT + " " + sessionId + " 0 66 1 111111\n");
        ws.send(MessageTypes.JOINCHAN + " " + sessionId + " 0 " + userChannel + " 9\n");
    }
}
