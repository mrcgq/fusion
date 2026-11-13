package ctbrec.sites.cam4;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.util.Locale;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class Cam4HttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(Cam4HttpClient.class);

    public Cam4HttpClient(Config config) {
        super("cam4", config);
    }

    @Override
    public synchronized boolean login() throws IOException {
        if (loggedIn) {
            return true;
        }

        boolean cookiesWorked = checkLoginSuccess();
        if (cookiesWorked) {
            loggedIn = true;
            LOG.debug("Logged in with cookies");
            return true;
        }

        String url = Cam4.BASE_URI + "/rest/v2.0/login";
        LOG.debug("Logging in {}", url);
        JSONObject bodyJson = new JSONObject();
        bodyJson.put("username", config.getSettings().cam4Username);
        bodyJson.put("password", config.getSettings().cam4Password);
        RequestBody requestBody = RequestBody.create(bodyJson.toString().getBytes(UTF_8), MediaType.parse(MIMETYPE_APPLICATION_JSON));
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(ORIGIN, Cam4.BASE_URI)
                .header(REFERER, Cam4.BASE_URI)
                .post(requestBody)
                .build();
        try (Response response = execute(req)) {
            String body = response.body().string();
            LOG.debug("Response: {} {}", response.code(), body);
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                return json.optInt("userId") != 0;
            } else {
                return false;
            }
        }
    }

    /**
     *  check, if the login worked by requesting unchecked mail
     * @throws IOException
     */
    public boolean checkLoginSuccess() throws IOException {
        String url = Cam4.BASE_URI + "/rest/v2.0/login/user";
        //String url = "http://login.cam4.com:1234/rest/v2.0/login/user";
        LOG.debug("Checkin login success by calling {}", url);
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                //.header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .header(REFERER, Cam4.BASE_URI + "/library")
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .build();
        try (Response response = execute(req)) {
            String body = response.body().string();
            LOG.debug("Response: {} {}", response.code(), body);
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                return json.optInt("userId") != 0;
            } else {
                return false;
            }
        }
    }

    protected double getTokenBalance() throws IOException {
        if(!loggedIn) {
            login();
        }

        throw new RuntimeException("Not implemented, yet");
    }
}
