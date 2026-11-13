package ctbrec.sites.showup;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.Cookie;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Semaphore;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.ACCEPT_LANGUAGE;
import static ctbrec.io.HttpConstants.USER_AGENT;

public class ShowupHttpClient extends HttpClient {

    private String csrfToken;
    private final Semaphore requestThrottle = new Semaphore(8, true);

    protected ShowupHttpClient(Config config) {
        super("showup", config);
        setCookie("accept_rules", "true");
        setCookie("category", "all");
    }

    public void setCookie(String name, String value) {
        Cookie cookie = new Cookie.Builder()
                .domain("showup.tv")
                .name(name)
                .value(value)
                .build();

        Map<String, List<Cookie>> cookies = cookieJar.getCookies();
        List<Cookie> cookiesForDomain = cookies.computeIfAbsent(cookie.domain(), k -> new ArrayList<>());
        cookiesForDomain.removeIf(existingCookie -> Objects.equals(existingCookie.name(), cookie.name()));
        cookiesForDomain.add(cookie);
    }

    @SuppressWarnings("unused")
    public String getCsrfToken() throws IOException {
        if (csrfToken == null) {
            Request req = new Request.Builder()
                    .url(Showup.BASE_URL + "/site/log_in")
                    .header(USER_AGENT, config.getSettings().httpUserAgent)
                    .build();

            try (Response response = execute(req)) {
                if (response.isSuccessful()) {
                    String body = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                    Matcher m = Pattern.compile("var csrf = '(.*?)';").matcher(body);
                    if (m.matches()) {
                        csrfToken = m.group(1);
                    } else {
                        throw new RuntimeException("CSRF Token not found"); // NOSONAR
                    }
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            }
        }
        return csrfToken;
    }

    @Override
    public Response execute(Request req) throws IOException {
        try {
            requestThrottle.acquire();
            return super.execute(req);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted during request");
        } finally {
            requestThrottle.release();
        }
    }

    @Override
    public boolean login() throws IOException {
        return checkLoginSuccess();
        //        Settings settings = config.getSettings();
        //        FormBody body = new FormBody.Builder()
        //                .add("is_ajax", "1")
        //                .add("email", settings.showupUsername)
        //                .add("password", settings.showupPassword)
        //                .add("remember", "1")
        //                .build();
        //        Request req = new Request.Builder()
        //                .url(Showup.BASE_URL)
        //                .header(USER_AGENT, config.getSettings().httpUserAgent)
        //                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
        //                .header(REFERER, Showup.BASE_URL + '/')
        //                .header(ORIGIN, Showup.BASE_URL)
        //                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
        //                .post(body)
        //                .build();
        //
        //        try (Response response = execute(req)) {
        //            if (response.isSuccessful()) {
        //                String responseBody = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
        //                if (responseBody.startsWith("{")) {
        //                    JSONObject json = new JSONObject(responseBody);
        //                    return json.optString("status").equalsIgnoreCase("success");
        //                } else {
        //                    return false;
        //                }
        //            } else {
        //                throw new HttpException(response.code(), response.message());
        //            }
        //        }
    }

    public boolean checkLoginSuccess() throws IOException {
        Request req = new Request.Builder()
                .url(Showup.BASE_URL)
                .header(ACCEPT_LANGUAGE, "pl")
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .build();
        try (Response response = execute(req)) {
            if (response.isSuccessful()) {
                String responseBody = Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string();
                loggedIn = responseBody.contains("Wyloguj");
                return loggedIn;
            } else {
                return false;
            }
        }
    }
}
