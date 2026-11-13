package ctbrec.sites.streamray;

import ctbrec.Config;
import ctbrec.StringUtil;
import ctbrec.io.HttpClient;
import okhttp3.Cookie;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Locale;

import static ctbrec.io.HttpConstants.*;


public class StreamrayHttpClient extends HttpClient {

    public StreamrayHttpClient(Config config) {
        super("streamray", config);
    }

    @Override
    public boolean login() throws IOException {
        String token = getUserToken();
        if (StringUtil.isBlank(token)) {
            return false;
        } else {
            boolean isSuccess = checkLoginSuccess();
            if (isSuccess) {
                return true;
            } else {
                updateToken();
                return checkLoginSuccess();
            }
        }
    }

    private void updateToken() {
        Request req = new Request.Builder()
                .url(Streamray.BASE_URI)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .build();
        try (Response response = execute(req)) {
            // nothing to do, we just call the base URI to get the cookie
        } catch (Exception ex) {
            // fail silently
        }
    }

    private boolean checkLoginSuccess() {
        String token = getUserToken();
        Request req = new Request.Builder()
                .url(Streamray.API_URL + "/members/me/balance/")
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(AUTHORIZATION, "Bearer " + token)
                .header(REFERER, Streamray.BASE_URI + "/")
                .header(ORIGIN, Streamray.BASE_URI)
                .build();
        try (Response response = execute(req)) {
            if (response.isSuccessful()) {
                String content = response.body().string();
                JSONObject json = new JSONObject(content);
                return json.has("balance");
            } else {
                return false;
            }
        } catch (Exception ex) {
            return false;
        }
    }

    public String getUserToken() {
        try {
            Cookie cookie = getCookieJar().getCookie(HttpUrl.parse(Streamray.BASE_URI), "memberToken");
            String token = cookie.value();
            return token;
        } catch (Exception e) {
            return "";
        }
    }
}
