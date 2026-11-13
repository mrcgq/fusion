package ctbrec.ui.sites.jasmin;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.ui.ExternalBrowser;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class LiveJasminElectronLoginDialog {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminElectronLoginDialog.class);
    public static final String URL = LiveJasmin.baseUrl + "/en/auth/login";
    private CookieJar cookieJar;
    private ExternalBrowser browser;

    public LiveJasminElectronLoginDialog(CookieJar cookieJar) throws IOException {
        this.cookieJar = cookieJar;
        browser = ExternalBrowser.getInstance();
        try {
            var config = new JSONObject();
            config.put("url", URL);
            config.put("w", 640);
            config.put("h", 720);
            var msg = new JSONObject();
            msg.put("config", config);
            browser.run(msg, msgHandler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Couldn't wait for login dialog", e);
        } catch (IOException e) {
            LOG.debug("Error while starting the browser or communication to it", e);
        } finally {
            browser.close();
        }
    }

    private Consumer<String> msgHandler = line -> {
        if(!line.startsWith("{")) {
            System.err.println(line); // NOSONAR
        } else {
            var json = new JSONObject(line);
            if(json.has("url")) {
                var url = json.getString("url");
                if(url.endsWith("/auth/login")) {
                    try {
                        String username = Config.getInstance().getSettings().livejasminUsername;
                        if (username != null && !username.trim().isEmpty()) {
                            browser.executeJavaScript("document.querySelector('#login_form input[name=\"username\"]').value = '" + username + "';");
                        }
                        String password = Config.getInstance().getSettings().livejasminPassword;
                        if (password != null && !password.trim().isEmpty()) {
                            password = password.replace("'", "\\'");
                            browser.executeJavaScript("document.querySelector('#login_form input[name=\"password\"]').value = '" + password + "';");
                        }
                        browser.executeJavaScript("document.getElementById('header_container').setAttribute('style', 'display:none');");
                        browser.executeJavaScript("document.getElementById('footer').setAttribute('style', 'display:none');");
                        browser.executeJavaScript("document.getElementById('react-container').setAttribute('style', 'display:none');");
                        browser.executeJavaScript("document.getElementById('inner_container').setAttribute('style', 'padding: 0; margin: 1em');");
                        browser.executeJavaScript("document.querySelector('div[class~=\"content_box\"]').setAttribute('style', 'margin: 1em');");
                    } catch(Exception e) {
                        LOG.warn("Couldn't auto fill username and password", e);
                    }
                }
                if(json.has("cookies")) {
                    var cookiesFromBrowser = json.getJSONArray("cookies");
                    for (var i = 0; i < cookiesFromBrowser.length(); i++) {
                        var cookie = cookiesFromBrowser.getJSONObject(i);
                        Builder b = new Cookie.Builder()
                                .path("/")
                                .domain(LiveJasmin.baseDomain)
                                .name(cookie.getString("name"))
                                .value(cookie.getString("value"))
                                .expiresAt(0);
                        Cookie c = b.build();
                        cookieJar.saveFromResponse(HttpUrl.parse(LiveJasmin.baseUrl), Collections.singletonList(c));
                    }
                }
                if(url.contains("/member/")) {
                    try {
                        browser.close();
                    } catch(IOException e) {
                        LOG.error("Couldn't send close request to browser", e);
                    }
                }
            }
        }
    };
}
