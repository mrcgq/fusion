package ctbrec.sites.mfc;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import okhttp3.Request;
import okhttp3.Response;

public class ServerConfig {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConfig.class);

    List<String> ajaxServers;
    List<String> videoServers;
    List<String> chatServers;
    Map<String, String> h5Servers;
    Map<String, String> wsServers;
    Map<String, String> wzobsServers;
    Map<String, String> ngVideoServers;

    public ServerConfig(MyFreeCams mfc) throws IOException {
        String url = mfc.getBaseUrl() + "/_js/serverconfig.js";
        LOG.debug("Loading server config from {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "en-US,en;q=0.5")
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ORIGIN, mfc.getBaseUrl())
                .header(REFERER, mfc.getBaseUrl())
                .header(CONNECTION, KEEP_ALIVE)
                .build();
        Response resp = mfc.getHttpClient().execute(req);
        String json = resp.body().string();

        JSONObject serverConfig = new JSONObject(json);
        ajaxServers = parseList(serverConfig, "ajax_servers");
        videoServers = parseList(serverConfig, "video_servers");
        chatServers = parseList(serverConfig, "chat_servers");
        h5Servers = parseMap(serverConfig, "h5video_servers");
        wsServers = parseMap(serverConfig, "websocket_servers");
        wzobsServers = parseMap(serverConfig, "wzobs_servers");
        ngVideoServers = parseMap(serverConfig, "ngvideo_servers");
    }

    private static Map<String, String> parseMap(JSONObject serverConfig, String name) {
        JSONObject servers = serverConfig.getJSONObject(name);
        Map<String, String> result = new HashMap<>();
        for (String key : servers.keySet()) {
            result.put(key, servers.getString(key));
        }
        return result;
    }

    private static List<String> parseList(JSONObject serverConfig, String name) {
        JSONArray servers = serverConfig.getJSONArray(name);
        List<String> result = new ArrayList<>(servers.length());
        for (Object server : servers) {
            result.add((String) server);
        }
        return result;
    }

    public boolean isOnHtml5VideoServer(SessionState state) {
        int camserv = getCamServ(state);
        return isOnObsServer(state)
                || h5Servers.containsKey(Integer.toString(camserv))
                || (camserv >= 904 && camserv <= 915
                || camserv >= 938 && camserv <= 960);
    }

    public boolean isOnObsServer(SessionState state) {
        return isOnWzObsVideoServer(state) || isOnNgServer(state);
    }

    public boolean isOnWzObsVideoServer(SessionState state) {
        int camserv = getCamServ(state);
        return wzobsServers.containsKey(Integer.toString(camserv));
    }

    public boolean isOnNgServer(SessionState state) {
        int camserv = getCamServ(state);
        return ngVideoServers.containsKey(Integer.toString(camserv));
    }

    private static int getCamServ(SessionState state) {
        int camserv = Optional.ofNullable(state).map(SessionState::getU).map(User::getCamserv).orElse(-1);
        return camserv;
    }
}
