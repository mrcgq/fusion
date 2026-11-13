package ctbrec.sites.streamate;

import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONObject;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.*;

@Slf4j
public class StreamateHttpClient extends HttpClient {

    private static final String SAKEY_KEY = "sakey";

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private Long userId;
    @Getter
    private String saKey = "";
    @Getter
    private String userNickname = "";
    private String xsrfToken = "";

    public StreamateHttpClient(Config config) {
        super("streamate", config);

        // this cookie is needed for the search
        Cookie searchCookie = new Cookie.Builder()
                .domain("streamate.com")
                .name("Xld_rct")
                .value("1")
                .build();
        getCookieJar().saveFromResponse(HttpUrl.parse(Streamate.BASE_URL), Collections.singletonList(searchCookie));

        // try to load sakey from cookie
        try {
            Cookie cookie = getCookieJar().getCookie(HttpUrl.parse(Streamate.API_URL), SAKEY_KEY);
            saKey = cookie.value();
        } catch (NoSuchElementException e) {
            // ignore
        }
    }

    private void loadXsrfToken() {
        // do a first request to get cookies and stuff
        Request req = new Request.Builder() //
                .url(Streamate.BASE_URL + "/initialData.js") //
                .header(USER_AGENT, config.getSettings().httpUserAgent) //
                .header(COOKIE, "smtid=" + UUID.randomUUID() + "; Xld_rct=1;") //
                .header(REFERER, Streamate.BASE_URL)
                .build();
        try (Response resp = execute(req)) {
            if (resp.code() == 200) {
                String body = Objects.requireNonNull(resp.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                log.info("Initial request was fine, Extracting XSRF token");
                Matcher m = Pattern.compile("\"xsrfToken\":\"(.*?)\"", Pattern.DOTALL).matcher(body);
                if (m.find()) {
                    xsrfToken = m.group(1);
                    log.info("XSRF token is {}", xsrfToken);
                } else {
                    log.warn("Couldn't find xsrf in initialData.js");
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        } catch (IOException e) {
            log.error("Initial request failed", e);
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
            log.info("Logged in with cookies");
            return true;
        }

        loggedIn = loginWithoutCookies();
        return loggedIn;
    }

    private synchronized boolean loginWithoutCookies() throws IOException {
        String username = config.getSettings().streamateUsername;
        String password = config.getSettings().streamatePassword;
        if (StringUtil.isBlank(username) || StringUtil.isBlank(password)) {
            return false;
        }
        JSONObject loginRequest = new JSONObject();
        loginRequest.put("allowLoginRedirection", true);
        loginRequest.put("email", username);
        loginRequest.put("password", password);
        loginRequest.put("referrerId", 0);
        loginRequest.put("siteId", 1);
        loginRequest.put("siteType", "premium");
        loginRequest.put("tzOffsetMinutes", 0);
        RequestBody body = RequestBody.Companion.create(loginRequest.toString(), JSON);
        Request login = newRequestBuilder()
                .url(Streamate.API_URL + "/api/member/login")
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, Streamate.BASE_URL)
                .header(REFERER, Streamate.BASE_URL + '/')
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .header(X_XSRF_TOKEN, getXsrfToken())
                .post(body)
                .build();
        try (Response response = client.newCall(login).execute()) {
            String content = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(content);
                loggedIn = json.has(SAKEY_KEY);
                saKey = json.optString(SAKEY_KEY);
                JSONObject account = json.getJSONObject("account");
                userId = account.getLong("userid");
                userNickname = account.getString("nickname");
            } else {
                throw new IOException("Login failed: " + response.code() + " " + response.message());
            }
        }
        return loggedIn;
    }

    protected String getXsrfToken() {
        if (xsrfToken.isEmpty()) {
            loadXsrfToken();
        }
        return xsrfToken;
    }

    public Request.Builder newRequestBuilder() {
        return new Request.Builder() // @formatter:off
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, Streamate.BASE_URL)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST); // @formatter:on
    }

    /**
     * Check, if the login worked by loading the favorites
     */
    public boolean checkLoginSuccess() {
        String url = Streamate.BASE_URL + "/api/search/v1/favorites?host=streamate.com&domain=streamate.com";
        url = url + "&page_number=1&results_per_page=48&sakey=" + saKey + "&userid=" + userId;
        Request request = newRequestBuilder().url(url).build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                String content = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                JSONObject json = new JSONObject(content);
                return json.optString("status").equals("SM_OK");
            } else {
                return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public Long getUserId() throws IOException {
        if (userId == null) {
            loginWithoutCookies();
        }
        return userId;
    }

    public String getCookieValue(String name) {
        try {
            getXsrfToken();
            Cookie cookie = getCookieJar().getCookie(HttpUrl.parse(Streamate.API_URL), name);
            return cookie.value();
        } catch (Exception ex) {
            return null;
        }
    }
}
