package ctbrec.ui.sites.showup;

import java.io.IOException;
import java.util.Collections;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.sites.showup.Showup;
import ctbrec.ui.ExternalBrowser;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class ShowupElectronLoginDialog {

    private static final Logger LOG = LoggerFactory.getLogger(ShowupElectronLoginDialog.class);
    public static final String DOMAIN = "showup.tv";
    public static final String URL = Showup.BASE_URL;
    private CookieJar cookieJar;
    private ExternalBrowser browser;
    private boolean firstCall = true;

    public ShowupElectronLoginDialog(CookieJar cookieJar) throws IOException {
        this.cookieJar = cookieJar;
        browser = ExternalBrowser.getInstance();
        try {
            var config = new JSONObject();
            config.put("url", URL);
            config.put("w", 640);
            config.put("h", 480);
            config.put("userAgent", Config.getInstance().getSettings().httpUserAgent);
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
        if(!line.startsWith("{")) {
            System.err.println(line); // NOSONAR
        } else {
            var json = new JSONObject(line);
            if(json.has("url")) {
                var url = json.getString("url");
                LOG.debug(url);
                if(url.contains("/site/accept_rules")) {
                    try {
                        Thread.sleep(500);
                        var simplify = new String[] { "document.getElementById(\"acceptrules\").submit();" };
                        for (String js : simplify) {
                            browser.executeJavaScript(js);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Couldn't auto fill username and password for Showup", e);
                    } catch (Exception e) {
                        LOG.warn("Couldn't auto fill username and password for Showup", e);
                    }
                } else if (url.equals(URL + '/') && firstCall) {
                    firstCall = false;
                    try {
                        Thread.sleep(500);
                        browser.executeJavaScript("user.openLoginPopUp();");
                        String username = Config.getInstance().getSettings().showupUsername;
                        if (username != null && !username.trim().isEmpty()) {
                            browser.executeJavaScript("$('input[name=\"email\"]').attr('value','" + username + "')");
                        }
                        String password = Config.getInstance().getSettings().showupPassword;
                        if (password != null && !password.trim().isEmpty()) {
                            password = password.replace("'", "\\'");
                            browser.executeJavaScript("$('input[name=\"password\"]').attr('value','" + password + "')");
                        }
                        browser.executeJavaScript("$('input[name=\"remember\"]').attr('value','true')");
                        return;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Couldn't auto fill username and password for Showup", e);
                    } catch (Exception e) {
                        LOG.warn("Couldn't auto fill username and password for Showup", e);
                    }
                }

                if (json.has("cookies")) {
                    var cookies = json.getJSONArray("cookies");
                    for (var i = 0; i < cookies.length(); i++) {
                        var cookie = cookies.getJSONObject(i);
                        if(cookie.getString("domain").contains(DOMAIN)) {
                            Builder b = new Cookie.Builder()
                                    .path(cookie.getString("path"))
                                    .domain(DOMAIN)
                                    .name(cookie.getString("name"))
                                    .value(cookie.getString("value"))
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
                            LOG.debug("Adding cookie {}={}", c.name(), c.value());
                            cookieJar.saveFromResponse(HttpUrl.parse(Showup.BASE_URL), Collections.singletonList(c));
                        }
                    }
                }
            }
        }
    };
}
