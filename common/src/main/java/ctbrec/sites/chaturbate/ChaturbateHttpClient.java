package ctbrec.sites.chaturbate;

import ctbrec.Config;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.NoSuchElementException;
import java.util.concurrent.Semaphore;

import static ctbrec.io.HttpConstants.REFERER;
import static ctbrec.io.HttpConstants.USER_AGENT;

@Slf4j
public class ChaturbateHttpClient extends HttpClient {

    private static final String PATH = "/auth/login/"; // NOSONAR
    protected String token;

    private static final Semaphore requestThrottle = new Semaphore(2, true);
    private static long lastRequest = 0;
    

    public ChaturbateHttpClient(Config config) {
        super("chaturbate", config);
    }
    
    @Override
    public String getEffectiveUserAgent() {
        if (flaresolverr != null) {
            return config.getSettings().flaresolverr.userAgent;
        } else {
            return config.getSettings().httpUserAgent;
        }
    }

    private void extractCsrfToken(Request request) {
        try {
            Cookie csrfToken = cookieJar.getCookie(request.url(), "csrftoken");
            token = csrfToken.value();
        } catch (NoSuchElementException e) {
            log.trace("CSRF token not found in cookies");
        }
    }

    public String getToken() throws IOException {
        if (token == null) {
            login();
        }
        return token;
    }

    @Override
    public boolean login() throws IOException {
        if (loggedIn) {
            return true;
        }
        if (checkLogin()) {
            loggedIn = true;
            log.debug("Logged in with cookies");
            return true;
        }
        Request login = new Request.Builder()
                .url(Chaturbate.baseUrl + PATH)
                .header(USER_AGENT, getEffectiveUserAgent())
                .build();
        try (var initResponse = client.newCall(login).execute()) {
            String content = initResponse.body().string();
            token = HtmlParser.getTag(content, "input[name=csrfmiddlewaretoken]").attr("value");
            log.debug("csrf token is {}", token);

            RequestBody body = new FormBody.Builder()
                    .add("username", Config.getInstance().getSettings().chaturbateUsername)
                    .add("password", Config.getInstance().getSettings().chaturbatePassword)
                    .add("next", "")
                    .add("csrfmiddlewaretoken", token)
                    .build();
            login = new Request.Builder()
                    .url(Chaturbate.baseUrl + PATH)
                    .header(REFERER, Chaturbate.baseUrl + PATH)
                    .header(USER_AGENT, getEffectiveUserAgent())
                    .post(body)
                    .build();

            try (var loginResponse = client.newCall(login).execute()) {
                if (loginResponse.isSuccessful()) {
                    content = loginResponse.body().string();
                    if (content.contains("Login, Chaturbate login")) {
                        loggedIn = false;
                    } else {
                        loggedIn = true;
                        extractCsrfToken(login);
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("Login failed: {}", ex.getMessage());
        }
        return loggedIn;
    }

    public boolean checkLogin() {
        String url = "https://chaturbate.com/api/ts/chatmessages/pm_users/?offset=0";
        Request req = new Request.Builder()
                .url(url)
                .header(USER_AGENT, getEffectiveUserAgent())
                .build();
        try (Response response = execute(req)) {
            boolean result = false;
            if (response.isSuccessful()) {
                String content = response.body().string();
                if (content.startsWith("[")) {
                    result = true;
                }
            }
            log.trace("Chaturbate client login result: {}, {}", result, response.body().string());
            return result;
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public Response execute(Request req) throws IOException {
        boolean throttled = req.url().host().contains("chaturbate.com");
        return executeThrottled(req, throttled);
    }

    private Response executeThrottled(Request req, boolean throttle) throws IOException {
        try {
            if (throttle) {
                acquireSlot();
            }
            
            Response resp = super.execute(req);
            
            extractCsrfToken(req);
            return resp;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted during request");
        } finally {
            if (throttle) {
                releaseSlot();
            }
        }
    }
    

    private static void acquireSlot() throws InterruptedException {
        long pauseBetweenRequests = Config.getInstance().getSettings().chaturbateMsBetweenRequests;
        requestThrottle.acquire();
        long now = System.currentTimeMillis();
        long millisSinceLastRequest = now - lastRequest;
        if (millisSinceLastRequest < pauseBetweenRequests) {
            Thread.sleep(pauseBetweenRequests - millisSinceLastRequest);
        }
    }

    private static void releaseSlot() {
        lastRequest = System.currentTimeMillis();
        requestThrottle.release();
    }
}
