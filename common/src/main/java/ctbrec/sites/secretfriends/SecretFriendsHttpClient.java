package ctbrec.sites.secretfriends;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static ctbrec.io.HttpConstants.*;

public class SecretFriendsHttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(SecretFriendsHttpClient.class);
    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private long userId;
    private String csrfToken;
    private String csrfTimestamp;
    private String csrfNotifyTimestamp;

    public SecretFriendsHttpClient(Config config) {
        super("secretfirends", config);
    }

    @Override
    public boolean login() throws IOException {
        if (loggedIn) {
            if (csrfToken == null) {
                loadCsrfToken();
            }
            return true;
        }

        // persisted cookies might let us log in
        if (checkLoginSuccess()) {
            loggedIn = true;
            LOG.debug("Logged in with cookies");
            if (csrfToken == null) {
                loadCsrfToken();
            }
            return true;
        }

        if (csrfToken == null) {
            loadCsrfToken();
        }

        String url = SecretFriends.BASE_URI + "/api/front/auth/login";
        JSONObject requestParams = new JSONObject();
        requestParams.put("loginOrEmail", config.getSettings().stripchatUsername);
        requestParams.put("password", config.getSettings().stripchatPassword);
        requestParams.put("remember", true);
        requestParams.put("csrfToken", csrfToken);
        requestParams.put("csrfTimestamp", csrfTimestamp);
        requestParams.put("csrfNotifyTimestamp", csrfNotifyTimestamp);
        RequestBody body = RequestBody.Companion.create(requestParams.toString(), JSON);
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, SecretFriends.BASE_URI)
                .header(REFERER, SecretFriends.BASE_URI)
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .post(body)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject resp = new JSONObject(response.body().string());
                if(resp.has("user")) {
                    JSONObject user = resp.getJSONObject("user");
                    userId = user.optLong("id");
                    return true;
                } else {
                    return false;
                }
            } else {
                LOG.info("Auto-Login failed: {} {} {}", response.code(), response.message(), url);
                return false;
            }
        }
    }

    private void loadCsrfToken() throws IOException {
        String url = SecretFriends.BASE_URI + "/api/front/v2/config/data?requestPath=%2F&timezoneOffset=0";
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, SecretFriends.BASE_URI)
                .header(REFERER, SecretFriends.BASE_URI)
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject resp = new JSONObject(response.body().string());
                JSONObject data = resp.getJSONObject("data");
                csrfToken = data.optString("csrfToken");
                csrfTimestamp = data.optString("csrfTimestamp");
                csrfNotifyTimestamp = data.optString("csrfNotifyTimestamp");
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
        userId = getUserId();
        String url = SecretFriends.BASE_URI + "/api/front/users/" + userId + "/favorites";
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, SecretFriends.BASE_URI)
                .header(REFERER, SecretFriends.BASE_URI + "/favorites")
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                return true;
            }
        } catch (Exception e) {
            LOG.info("Login check returned unsuccessful: {}", e.getLocalizedMessage());
        }
        return false;
    }

    public long getUserId() throws JSONException, IOException {
        if (userId == 0) {
            String url = SecretFriends.BASE_URI + "/api/front/users/username/" + config.getSettings().stripchatUsername;
            Request request = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                    .header(USER_AGENT, config.getSettings().httpUserAgent)
                    .header(ORIGIN, SecretFriends.BASE_URI)
                    .header(REFERER, SecretFriends.BASE_URI)
                    .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                    .build();
            try (Response response = execute(request)) {
                if (response.isSuccessful()) {
                    JSONObject resp = new JSONObject(response.body().string());
                    JSONObject user = resp.getJSONObject("user");
                    userId = user.optLong("id");
                } else {
                    throw new HttpException(url, response.code(), response.message());
                }
            }
        }
        return userId;
    }

    public String getCsrfNotifyTimestamp() {
        return csrfNotifyTimestamp;
    }

    public String getCsrfTimestamp() {
        return csrfTimestamp;
    }

    public String getCsrfToken() {
        return csrfToken;
    }
}
