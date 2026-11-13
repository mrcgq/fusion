package ctbrec.ui.sites.stripchat;

import ctbrec.Config;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.ui.ExternalBrowser;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

public class StripchatElectronLoginDialog {

    private static final Logger LOG = LoggerFactory.getLogger(StripchatElectronLoginDialog.class);
    public static final String DOMAIN = Stripchat.getDomain();
    public static final String URL = Stripchat.getBaseUri();
    private CookieJar cookieJar;
    private ExternalBrowser browser;

    public StripchatElectronLoginDialog(CookieJar cookieJar) throws IOException {
        this.cookieJar = cookieJar;
        browser = ExternalBrowser.getInstance();
        try {
            var config = new JSONObject();
            config.put("url", URL);
            config.put("w", 640);
            config.put("h", 640);
            var msg = new JSONObject();
            msg.put("config", config);
            browser.run(msg, msgHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't wait for login dialog", e);
        } finally {
            browser.close();
        }
    }

    private Consumer<String> msgHandler = line -> {
        if (!line.startsWith("{")) {
            System.err.println(line); // NOSONAR
        } else {
            var json = new JSONObject(line);
            if (json.has("url")) {
                var url = json.getString("url");

                if (url.endsWith(DOMAIN) || url.endsWith(DOMAIN + '/')) {
                    try {
                        browser.executeJavaScript("document.querySelector('button[class~=\"btn-visitors-agreement-accept\"]').click();");
                        browser.executeJavaScript("document.querySelector('div[class~=\"header-dropdown\"] a[class~=\"dropdown-link\"]').click();");
                        browser.executeJavaScript("document.querySelector('a[class~=\"btn\"][href*=\"login\"]').click();");
                        String username = Config.getInstance().getSettings().stripchatUsername;
                        if (username != null && !username.trim().isEmpty()) {
                            browser.executeJavaScript("document.querySelector('#login_login_or_email').value = '" + username + "';");
                        }
                        String password = Config.getInstance().getSettings().stripchatPassword;
                        if (password != null && !password.trim().isEmpty()) {
                            password = password.replace("'", "\\'");
                            browser.executeJavaScript("document.querySelector('#login_password').value = '" + password + "';");
                        }
                        browser.executeJavaScript("document.querySelector('#recaptcha-checkbox-border').click();");
                        browser.executeJavaScript("document.querySelector('*[class~=btn-login]').addEventListener('click', function() {window.setTimeout(function() {location.reload()}, 2000)});");
                    } catch (Exception e) {
                        LOG.warn("Couldn't auto fill username and password for Stripchat", e);
                    }
                }

                if (json.has("cookies")) {
                    var cookiesFromBrowser = json.getJSONArray("cookies");
                    var sessionCookieFound = false;
                    for (var i = 0; i < cookiesFromBrowser.length(); i++) {
                        var cookie = cookiesFromBrowser.getJSONObject(i);
                        if (cookie.getString("domain").contains(DOMAIN)) {
                            var domain = cookie.getString("domain");
                            if (domain.startsWith(".")) {
                                domain = domain.substring(1);
                            }
                            var c = createCookie(domain, cookie);
                            cookieJar.saveFromResponse(HttpUrl.parse(url), Collections.singletonList(c));
                            c = createCookie(DOMAIN, cookie);
                            cookieJar.saveFromResponse(HttpUrl.parse(Stripchat.getBaseUri()), Collections.singletonList(c));
                            if (c.name().contains("_com_sessionId")) {
                                sessionCookieFound = true;
                            }
                        }
                    }

                    if (sessionCookieFound) {
                        try {
                            browser.close();
                        } catch (IOException e) {
                            LOG.error("Couldn't send close request to browser", e);
                        }
                    }
                }
            }
        }
    };

    private Cookie createCookie(String domain, JSONObject cookie) {
        Builder b = new Cookie.Builder()
                .path(cookie.getString("path"))
                .domain(domain)
                .name(cookie.getString("name"))
                .value(cookie.getString("value"))
                .expiresAt(Double.valueOf(cookie.optDouble("expirationDate")).longValue()); // NOSONAR
        if (cookie.optBoolean("hostOnly")) {
            b.hostOnlyDomain(domain);
        }
        if (cookie.optBoolean("httpOnly")) {
            b.httpOnly();
        }
        if (cookie.optBoolean("secure")) {
            b.secure();
        }
        return b.build();
    }
}
