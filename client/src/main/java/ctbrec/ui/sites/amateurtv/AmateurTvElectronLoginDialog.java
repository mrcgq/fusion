package ctbrec.ui.sites.amateurtv;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.ui.ExternalBrowser;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class AmateurTvElectronLoginDialog {

    private static final Logger LOG = LoggerFactory.getLogger(AmateurTvElectronLoginDialog.class);
    public static final String DOMAIN = "amateur.tv";
    public static final String URL = AmateurTv.BASE_URL;
    private CookieJar cookieJar;
    private ExternalBrowser browser;

    public AmateurTvElectronLoginDialog(CookieJar cookieJar) throws IOException {
        this.cookieJar = cookieJar;
        browser = ExternalBrowser.getInstance();
        try {
            var config = new JSONObject();
            config.put("url", URL);
            config.put("w", 640);
            config.put("h", 480);
            var msg = new JSONObject();
            msg.put("config", config);
            browser
            .onReady(this::onReady)
            .run(msg, msgHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't wait for login dialog", e);
        } finally {
            browser.close();
        }
    }

    private Consumer<String> msgHandler = line -> {
        if (!line.startsWith("{")) {
            LOG.error("Didn't received a JSON object {}", line);
        } else {
            var json = new JSONObject(line);

            var loginSuccessful = false;
            if (json.has("cookies")) {
                var cookiesFromBrowser = json.getJSONArray("cookies");
                for (var i = 0; i < cookiesFromBrowser.length(); i++) {
                    var cookie = cookiesFromBrowser.getJSONObject(i);
                    if (cookie.getString("domain").contains(DOMAIN)) {
                        Builder b = new Cookie.Builder()
                                .path(cookie.getString("path"))
                                .domain(DOMAIN)
                                .name(cookie.getString("name"))
                                .value(cookie.getString("value"))
                                .expiresAt((long) cookie.optDouble("expirationDate") * 1000l);
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
                        cookieJar.saveFromResponse(HttpUrl.parse(AmateurTv.BASE_URL), Collections.singletonList(c));
                        LOG.debug("{}={}", c.name(), c.value());
                        if (Objects.equals(c.name(), "userType") && Objects.equals(c.value(), "registered")) {
                            loginSuccessful = true;
                        }
                    }
                }
            }

            if (loginSuccessful) {
                try {
                    browser.close();
                    return;
                } catch (IOException e) {
                    LOG.error("Couldn't send shutdown request to external browser", e);
                }
            }

            try {
                browser.executeJavaScript("document.querySelector('div[class~=\"cy_ubCoins\"]') != null")
                .thenAccept(b -> {
                    LOG.debug("Result: {}", b);
                    if (Boolean.TRUE.equals(b)) {
                        try {
                            browser.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .exceptionally(ex -> {LOG.error("Error", ex); return null;});

                browser.executeJavaScript("if (!loginDialogVisible) { document.querySelector('button').innerHTML.indexOf('I agree') >= 0 && document.querySelector('button').click(); }");
                browser.executeJavaScript("if (!loginDialogVisible) { document.querySelector('button[aria-label=\"open drawer\"]').click(); }"); // open the burger menu to get to the login button
                browser.executeJavaScript("if (!loginDialogVisible) { document.querySelectorAll('button').forEach(function(b) { if (b.textContent === 'Log in') b.click(); }); }"); // click the login button to open the login dialog
                browser.executeJavaScript("loginDialogVisible = document.querySelectorAll('div[class~=\"MuiDialog-container\"]').length > 1");
                browser.executeJavaScript("if (loginDialogVisible) throw new Error(\"Stop execution right here\")");
            } catch(Exception e) {
                LOG.warn("Couldn't auto fill username and password for Amateur.TV", e);
            }
        }
    };

    private void onReady() {
        try {
            browser.executeJavaScript("let loginDialogVisible = document.querySelectorAll('div[class~=\"MuiDialog-container\"]').length > 1");
        } catch(Exception e) {
            LOG.warn("Couldn't auto fill username and password for Amateur.TV", e);
        }
    }
}
