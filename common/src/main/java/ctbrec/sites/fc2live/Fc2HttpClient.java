package ctbrec.sites.fc2live;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.io.CookieJarImpl;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class Fc2HttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(Fc2HttpClient.class);

    public Fc2HttpClient(Config config) {
        super("fc2live", config);
    }

    @Override
    protected CookieJarImpl createCookieJar() {
        return new Fc2CookieJar();
    }

    @Override
    public boolean login() throws IOException {
        if (loggedIn) {
            return true;
        }

        if(checkLogin()) {
            loggedIn = true;
            LOG.debug("Logged in with cookies");
            return true;
        }

        String username = Config.getInstance().getSettings().fc2liveUsername;
        String password = Config.getInstance().getSettings().fc2livePassword;
        RequestBody body = new FormBody.Builder()
                .add("email", username)
                .add("pass", password)
                .add("image.x", "0")
                .add("image.y", "0")
                .add("done", "")
                .build();
        Request req = new Request.Builder()
                .url("https://secure.id.fc2.com/index.php?mode=login&switch_language=en")
                .header(REFERER, "https://fc2.com/en/login.php")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .post(body)
                .build();
        try(Response resp = execute(req)) {
            if(resp.isSuccessful()) {
                String page = resp.body().string();
                LOG.debug(page);
                if(page.contains("Invalid e-mail address or password")) {
                    return false;
                } else {
                    LOG.debug("Calling https://secure.id.fc2.com/?login=done");
                    req = new Request.Builder()
                            .url("https://secure.id.fc2.com/?login=done")
                            .header(REFERER, "https://secure.id.fc2.com/index.php?mode=login&switch_language=en")
                            .build();
                    try (Response resp2 = execute(req)) {
                        if (resp.isSuccessful()) {
                            LOG.debug("Login complete");
                            loggedIn = true;
                            return true;
                        } else {
                            LOG.debug("Login failed");
                            loggedIn = false;
                            return false;
                        }
                    }
                }
            } else {
                LOG.error("Login failed {} {}", resp.code(), resp.message());
                return false;
            }
        }
    }

    private boolean checkLogin() throws IOException {
        Request req = new Request.Builder().url(Fc2Live.BASE_URL + "/api/favoriteManager.php").build();
        try (Response response = execute(req)) {
            if (response.isSuccessful()) {
                String content = response.body().string();
                JSONObject json = new JSONObject(content);
                return json.optInt("status") == 1;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    @Override
    public WebSocket newWebSocket(Request req, WebSocketListener webSocketListener) {
        return client.newWebSocket(req, webSocketListener);
    }
}
