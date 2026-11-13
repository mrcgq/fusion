package ctbrec.sites.flirt4free;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;

public class Flirt4FreeHttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(Flirt4FreeHttpClient.class);

    public Flirt4FreeHttpClient(Config config) {
        super("flirt4free", config);
    }

    @Override
    public boolean login() throws IOException {
        if(loggedIn) {
            return true;
        }

        // persisted cookies might let us log in
        if(checkLoginSuccess()) {
            loggedIn = true;
            LOG.debug("Logged in with cookies");
            return true;
        }

        String url = Flirt4Free.BASE_URI + "/my-account/";
        FormBody body = new FormBody.Builder()
                .add("a", "login")
                .add("small", "0")
                .add("acctCheck", "1")
                .add("login[remember_me]", "1")
                .add("send_to_page", Flirt4Free.BASE_URI)
                .add("query_string", "")
                .add("login[username]", Config.getInstance().getSettings().flirt4freeUsername)
                .add("login[password]", Config.getInstance().getSettings().flirt4freePassword)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .post(body)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                List<String> cookies = response.headers("Set-Cookie");
                return cookies.toString().contains("has_logged_in=1") && cookies.toString().contains("CHAT_USER=" + Config.getInstance().getSettings().flirt4freeUsername);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    /**
     *  check, if the login worked
     * @throws IOException
     */
    public boolean checkLoginSuccess() throws IOException {
        String url = Flirt4Free.BASE_URI + "/my-account/";
        Request request = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try(Response response = execute(request)) {
            if(response.isSuccessful()) {
                String body = response.body().string();
                return body.contains("username-logged-in");
            } else {
                return false;
            }
        }
    }
}
