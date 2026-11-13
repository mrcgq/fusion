package ctbrec.sites.cherrytv;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Objects;

import static ctbrec.io.HttpConstants.*;

public class CherryTvHttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(CherryTvHttpClient.class);

    public CherryTvHttpClient(Config config) {
        super("cherrytv", config);
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

        JSONObject body = new JSONObject()
                .put("operationName", "authenticateUser")
                .put("variables", new JSONObject()
                        .put("accountName", config.getSettings().cherryTvUsername)
                        .put("password", config.getSettings().cherryTvPassword)
                        .put("sourceDomain", "cherry.tv")
                )
                .put("extensions", new JSONObject()
                        .put("persistedQuery", new JSONObject()
                                .put("version", 1)
                                .put("sha256Hash", "dc7c4f9040e6bbbd9168e3e10867115117c52689beac68c2066c6e695759911a")
                        )
                );


        RequestBody requestBody = RequestBody.create(body.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(CherryTv.BASE_URL + "/graphql")
                .header(REFERER, CherryTv.BASE_URL)
                .header(ORIGIN, CherryTv.BASE_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .post(requestBody)
                .build();

        LOG.debug("Logging in: {}", request.url());
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject resp = new JSONObject(Objects.requireNonNull(response.body()).string());
                if (resp.has("data")) {
                    JSONObject data = resp.getJSONObject("data");
                    JSONObject login = data.getJSONObject("login");
                    loggedIn = login.optBoolean("success");
                    String jwt = login.optString("token");
                    saveAsSessionCookie(jwt);
                    LOG.debug("Login successful");
                    return loggedIn;
                } else {
                    LOG.error(resp.toString(2));
                    return false;
                }
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }

    private void saveAsSessionCookie(String jwt) {
        HttpUrl url = HttpUrl.parse(CherryTv.BASE_URL);
        Objects.requireNonNull(url);
        long expiresAt = Instant.now().plus(1, ChronoUnit.DAYS).getEpochSecond();
        Cookie sessionCookie = new Cookie.Builder()
                .name("session")
                .value(jwt)
                .expiresAt(expiresAt)
                .domain(Objects.requireNonNull(url.topPrivateDomain()))
                .path("/")
                .secure().httpOnly()
                .build();
        getCookieJar().saveFromResponse(url, Collections.singletonList(sessionCookie));
    }

    private boolean checkLoginSuccess() {
        String url = CherryTv.BASE_URL + "/graphql?operationName=FindFollowings&variables={\"cursor\":\"0\",\"limit\":20}&extensions={\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"b4e8b260e7ab5958011b7a242ecbc13158f05b90380c0dbcafd6a8e3b4c96414\"}}";
        Request request = new Request.Builder()
                .url(url)
                .header(REFERER, CherryTv.BASE_URL)
                .header(ORIGIN, CherryTv.BASE_URL)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();

        try (Response response = execute(request)) {
            String body = Objects.requireNonNull(response.body()).string();
            LOG.debug("Login body: {}", body);
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(body);
                if (json.has("errors")) {
                    LOG.error(json.toString(2));
                    return false;
                } else {
                    return json.optString("__typename").equals("FollowingList");
                }
            }
            return false;
        } catch (Exception e) {
            LOG.error("Login check failed", e);
            return false;
        }
    }
}
