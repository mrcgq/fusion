package ctbrec.ui.sites.streamray;

import ctbrec.Config;
import ctbrec.sites.streamray.Streamray;
import ctbrec.ui.ExternalBrowser;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

@Slf4j
public class StreamrayElectronLoginDialog {

    public static final String DOMAIN = "streamray.com";
    private CookieJar cookieJar;


    public StreamrayElectronLoginDialog(CookieJar cookieJar) throws IOException {
        this.cookieJar = cookieJar;
        try (ExternalBrowser browser = ExternalBrowser.getInstance()) {
            var config = new JSONObject();
            config.put("url", Streamray.BASE_URI);
            config.put("w", 800);
            config.put("h", 600);
            config.put("userAgent", Config.getInstance().getSettings().httpUserAgent);
            var msg = new JSONObject();
            msg.put("config", config);
            browser.run(msg, msgHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't wait for login dialog", e);
        }
    }

    private final Consumer<String> msgHandler = line -> {
        if (!line.startsWith("{")) return;
        JSONObject json = new JSONObject(line);
        if (json.has("cookies")) {
            var cookies = json.getJSONArray("cookies");
            for (var i = 0; i < cookies.length(); i++) {
                var cookie = cookies.getJSONObject(i);
                if (cookie.getString("domain").contains(DOMAIN)) {
                    Builder b = new Cookie.Builder()
                            .path(cookie.getString("path"))
                            .domain(DOMAIN)
                            .name(cookie.optString("name"))
                            .value(cookie.optString("value"))
                            .expiresAt(Double.valueOf(cookie.optDouble("expirationDate")).longValue()); // NOSONAR
                    if (cookie.optBoolean("hostOnly")) {
                        b.hostOnlyDomain(DOMAIN);
                    }
                    if (cookie.optBoolean("httpOnly")) {
                        b.httpOnly();
                    }
                    if (cookie.optBoolean("secure")) {
                        b.secure();
                    }
                    Cookie c = b.build();
                    log.trace("Adding cookie {}={}", c.name(), c.value());
                    cookieJar.saveFromResponse(HttpUrl.parse(Streamray.BASE_URI), Collections.singletonList(c));
                } // if
            } // for
        }
    };
}
