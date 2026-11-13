package ctbrec.ui.sites.bonga;

import ctbrec.Config;
import ctbrec.sites.bonga.BongaCams;
import ctbrec.ui.ExternalBrowser;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Objects;
import java.util.function.Consumer;

public class BongaCamsElectronLoginDialog {

    private static final Logger LOG = LoggerFactory.getLogger(BongaCamsElectronLoginDialog.class);
    public static final String DOMAIN = "bongacams.com";
    private BongaCams site;
    private CookieJar cookieJar;
    private ExternalBrowser browser;

    public BongaCamsElectronLoginDialog(BongaCams site, CookieJar cookieJar) throws IOException {
        this.site = site;
        this.cookieJar = cookieJar;
        browser = ExternalBrowser.getInstance();
        try {
            var config = new JSONObject();
            config.put("url", site.getBaseUrl() + "/login");
            config.put("w", 640);
            config.put("h", 480);
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

    private final Consumer<String> msgHandler = line -> {
        if (!line.startsWith("{")) {
            LOG.error("Didn't received a JSON object {}", line);
        } else {
            var json = new JSONObject(line);
            if (json.has("url")) {
                var url = json.getString("url");
                if (url.endsWith("/login")) {
                    try {
                        Thread.sleep(500);
                        String username = Config.getInstance().getSettings().bongaUsername;
                        if (username != null && !username.trim().isEmpty()) {
                            browser.executeJavaScript("document.getElementById('log_in_username').value = '" + username + "';");
                        }
                        String password = Config.getInstance().getSettings().bongaPassword;
                        if (password != null && !password.trim().isEmpty()) {
                            password = password.replace("'", "\\'");
                            browser.executeJavaScript("document.getElementById('log_in_password').value = '" + password + "';");
                        }
                        var simplify = new String[]{
                                "$('div[class~=\"page_header\"]').css('display','none');",
                                "$('div[class~=\"header_bar\"]').css('display','none')",
                                "$('footer').css('display','none');",
                                "$('div[class~=\"footer_copy\"]').css('display','none')",
                                "$('div[class~=\"fancybox-overlay\"]').css('display','none');"
                        };
                        for (String js : simplify) {
                            browser.executeJavaScript(js);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        LOG.warn("Couldn't auto fill username and password for BongaCams", e);
                    } catch (Exception e) {
                        LOG.warn("Couldn't auto fill username and password for BongaCams", e);
                    }
                }

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
                                    .expiresAt((long) cookie.optDouble("expirationDate"));
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
                            cookieJar.saveFromResponse(HttpUrl.parse(BongaCamsElectronLoginDialog.this.site.getBaseUrl()), Collections.singletonList(c));
                        }
                    }
                }

                try {
                    if (Objects.equals(new URL(url).getPath(), "/")) {
                        browser.close();
                    }
                } catch (MalformedURLException e) {
                    LOG.error("Couldn't parse new url {}", url, e);
                } catch (IOException e) {
                    LOG.error("Couldn't send shutdown request to external browser", e);
                }
            }
        }
    };
}
