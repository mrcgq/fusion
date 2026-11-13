package ctbrec.sites.bonga;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.*;

public class BongaCamsHttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(BongaCamsHttpClient.class);
    private static final String SORT_COOKIE = "ls01";
    private final BongaCams site;
    private int userId = 0;

    public BongaCamsHttpClient(BongaCams site, Config config) {
        super("bongacams", config);
        this.site = site;
        addSortByPopularCookie();
    }

    /**
     * Adds a cookie, which defines the sort order for returned model lists
     */
    private void addSortByPopularCookie() {
        Cookie sortByCookie = new Cookie.Builder()
                .domain("bongacams.com")
                .name(SORT_COOKIE)
                .value("%7B%22limit%22%3A20%2C%22c_limit%22%3A10%2C%22th_type%22%3A%22live%22%2C%22sorting%22%3A%22popular%22%2C%22display%22%3A%22auto%22%7D")
                .build();

        Map<String, List<Cookie>> cookies = cookieJar.getCookies();
        for (Entry<String, List<Cookie>> entry : cookies.entrySet()) {
            List<Cookie> cookieList = entry.getValue();
            cookieList.removeIf(cookie -> cookie.name().equals(SORT_COOKIE));
            entry.getValue().add(sortByCookie);
        }
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

        return false;
    }

    /**
     * Check, if the login worked by requesting roomdata and looking
     *
     * @throws IOException if the roomdata couldn't be loaded
     */
    public boolean checkLoginSuccess() throws IOException {
        String modelName = getAnyModelName();
        // we request the roomData of a random model, because it contains
        // user data, if the user is logged in, which we can use to verify, that the login worked
        String url = site.getBaseUrl() + "/tools/amf.php";
        RequestBody body = new FormBody.Builder()
                .add("method", "getRoomData")
                .add("args[]", modelName)
                .add("args[]", "false")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, site.getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .post(body)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                if (json.optString("status").equals("success")) {
                    JSONObject userData = json.getJSONObject("userData");
                    userId = userData.optInt("userId");
                    return userId > 0;
                } else {
                    throw new IOException("Request was not successful: " + json.toString(2));
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    /**
     * Fetches the list of online models and returns the name of the first model
     */
    private String getAnyModelName() throws IOException {
        Request request = new Request.Builder()
                .url(site.getBaseUrl() + "/tools/listing_v3.php?livetab=female&online_only=true&is_mobile=true&offset=0")
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, site.getBaseUrl())
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                String content = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                JSONObject json = new JSONObject(content);
                if (json.optString("status").equals("success")) {
                    JSONArray jsonModels = json.getJSONArray("models");
                    JSONObject m = jsonModels.getJSONObject(0);
                    String name = m.getString("username");
                    return name;
                } else {
                    throw new IOException("Request was not successful: " + content);
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    public int getUserId() throws IOException {
        if (userId == 0) {
            login();
        }
        return userId;
    }
}
