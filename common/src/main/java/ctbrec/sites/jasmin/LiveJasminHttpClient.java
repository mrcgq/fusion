package ctbrec.sites.jasmin;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.Locale;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class LiveJasminHttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminHttpClient.class);

    protected LiveJasminHttpClient(Config config) {
        super("livejasmin", config);

        // delete all cookies, if we are guests, because old guest sessions cause
        // endless redirects
        if(Config.getInstance().getSettings().livejasminUsername.isEmpty()) {
            getCookieJar().clear();
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

    public synchronized boolean login(boolean renew) throws IOException {
        if (loggedIn && !renew) {
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

    public boolean checkLoginSuccess() throws IOException {
        OkHttpClient temp = client.newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        String url = "https://m." + LiveJasmin.baseDomain + "/en/member/favourite/get-favourite-list?ajax=1";
        Request request = new Request.Builder()
                .url(url)
                .addHeader(USER_AGENT, Config.getInstance().getSettings().httpUserAgentMobile)
                .addHeader(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .addHeader(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .addHeader(REFERER, LiveJasmin.baseUrl)
                .addHeader(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try(Response response = temp.newCall(request).execute()) {
            LOG.debug("Login Check {}: {} - {}", url, response.code(), response.message());
            if(response.isSuccessful()) {
                return true;
            } else {
                return false;
            }
        }
    }

    public String getSessionId() {
        Cookie sessionCookie = getCookieJar().getCookie(HttpUrl.parse(LiveJasmin.baseUrl), "session");
        if(sessionCookie != null) {
            return sessionCookie.value();
        } else {
            throw new NoSuchElementException("session cookie not found");
        }
    }
}
