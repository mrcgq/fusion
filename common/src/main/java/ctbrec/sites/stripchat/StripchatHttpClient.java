package ctbrec.sites.stripchat;

import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.Instant;
import java.util.Base64;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.net.URLDecoder;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

@Slf4j
public class StripchatHttpClient extends HttpClient {

    public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private long userId = 0;
    private String csrfToken;
    @Getter
    private String csrfTimestamp;
    @Getter
    private String csrfNotifyTimestamp;
    @Getter
    private String jwtToken;
    private Instant jwtTokenExp;

    public StripchatHttpClient(Config config) {
        super("stripchat", config);
    }

    @Override
    public boolean login() throws IOException {
        // refresh token if needed
        getCsrfToken();
        
        // already logged in
        if (loggedIn && isJwtTokenValid()) {
            return true;
        }
        
        // persisted cookies might let us log in
        if (checkLoginSuccess()) {
            loggedIn = true;
            log.debug("Logged in with cookies");
            return true;
        }


        String url = Stripchat.getBaseUri() + "/api/front/auth/login";
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
                .header(ORIGIN, Stripchat.getBaseUri())
                .header(REFERER, Stripchat.getBaseUri())
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .post(body)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject resp = new JSONObject(response.body().string());
                if (resp.has("user")) {
                    JSONObject user = resp.getJSONObject("user");
                    userId = user.optLong("id");
                    return true;
                } else {
                    return false;
                }
            } else {
                log.info("Auto-Login failed: {} {} {}", response.code(), response.message(), url);
                return false;
            }
        }
    }

    private void loadCsrfToken() throws IOException {
        String url = Stripchat.getBaseUri() + "/api/front/v3/config/initial?requestPath=%2F&timezoneOffset=0";
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, Stripchat.getBaseUri())
                .header(REFERER, Stripchat.getBaseUri())
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject resp = new JSONObject(response.body().string());
                JSONObject data = resp.getJSONObject("initial").getJSONObject("client");
                csrfToken = data.optString("csrfToken");
                csrfTimestamp = data.optString("csrfTimestamp");
                csrfNotifyTimestamp = data.optString("csrfNotifyTimestamp");
                log.debug("Stripchat token CSRF: {} [{}]", csrfToken, csrfNotifyTimestamp);
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void loadJwtToken() throws IOException {
        String url = Stripchat.getBaseUri() + "/api/front/v3/config/dynamic";
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, Stripchat.getBaseUri())
                .header(REFERER, Stripchat.getBaseUri())
                .header(CONTENT_TYPE, MIMETYPE_APPLICATION_JSON)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject resp = new JSONObject(response.body().string());
                JSONObject dynamic = resp.getJSONObject("dynamic");
                jwtToken = dynamic.optString("jwtToken");
                if (StringUtil.isNotBlank(jwtToken)) {
                    String[] parts = jwtToken.split("\\.");
                    if (parts.length > 1) {
                        String decString = new String(Base64.getDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                        JSONObject body  = new JSONObject(decString);
                        jwtTokenExp = Instant.ofEpochSecond(body.optLong("exp"));
                    }
                }
                log.debug("Stripchat token JWT: {} [{}]", jwtToken, jwtTokenExp);
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
        getJwtToken();
        getCsrfToken();
        return StringUtil.isNotBlank(jwtToken) && StringUtil.isNotBlank(csrfToken);
    }
    
    public long getUserId() throws JSONException, IOException {
        if (userId == 0) {
            String url = Stripchat.getBaseUri() + "/api/front/users/username/" + config.getSettings().stripchatUsername;
            Request request = new Request.Builder()
                    .url(url)
                    .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                    .header(USER_AGENT, config.getSettings().httpUserAgent)
                    .header(ORIGIN, Stripchat.getBaseUri())
                    .header(REFERER, Stripchat.getBaseUri())
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

    public JSONObject getAmpl() { 
        try {
            Cookie cookie = getCookieJar().getCookie(HttpUrl.parse(Stripchat.getBaseUri()), "baseAmpl");
            String json = URLDecoder.decode(cookie.value(), UTF_8);
            JSONObject ampl = new JSONObject(json);
            return ampl;
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    public String getCsrfNotifyTimestamp() {
        return csrfNotifyTimestamp;
    }

    public String getCsrfTimestamp() {
        return csrfTimestamp;
    }

    public String getCsrfToken() {
        try {
            if (!isTokenValid()) {
                loadCsrfToken();
            }
        } catch (Exception e) {
            log.debug("Invalid CSRF Token {}: {}", csrfToken, e.getMessage());
            csrfToken = "";
        }
        return csrfToken;
    }
    
    public String getJwtToken() {
        try {
            if (!isJwtTokenValid()) {
                loadJwtToken();
            }
        } catch (Exception e) {
            log.debug("Invalid JWT Token {}: {}", jwtToken, e.getMessage());
            jwtToken = "";
        }
        return jwtToken;
    }
    
    private boolean isJwtTokenValid() {
        if (StringUtil.isBlank(jwtToken) || jwtTokenExp == null) {
            return false;
        }
        return jwtTokenExp.isAfter(Instant.now());
    }
    
    private boolean isTokenValid() {
        if (StringUtil.isBlank(csrfToken) || StringUtil.isBlank(csrfTimestamp) || StringUtil.isBlank(csrfNotifyTimestamp)) {
            return false;
        }
        try {
            Instant notifyTime = Instant.parse(csrfNotifyTimestamp);
            return notifyTime.isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }
   
}
