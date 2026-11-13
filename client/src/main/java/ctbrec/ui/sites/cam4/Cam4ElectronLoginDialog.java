package ctbrec.ui.sites.cam4;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Consumer;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.sites.cam4.Cam4;
import ctbrec.ui.ExternalBrowser;
import okhttp3.Cookie;
import okhttp3.Cookie.Builder;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class Cam4ElectronLoginDialog {

    private static final Logger LOG = LoggerFactory.getLogger(Cam4ElectronLoginDialog.class);
    public static final String DOMAIN = "cam4.com";
    public static final String URL = Cam4.BASE_URI;
    private CookieJar cookieJar;
    private ExternalBrowser browser;

    private boolean dialogsClicked = false;
    private boolean loginDialogOpened = false;
    private Thread loginChecker;

    public Cam4ElectronLoginDialog(CookieJar cookieJar) throws IOException {
        this.cookieJar = cookieJar;
        browser = ExternalBrowser.getInstance();
        try {
            var config = new JSONObject();
            config.put("url", URL);
            config.put("w", 480);
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
        if(!line.startsWith("{")) {
            LOG.error("Didn't received a JSON object {}", line);
        } else {
            var json = new JSONObject(line);
            safeCookies(json);

            clickAwayCookieAndAgeAcknowlegde();
            openLoginDialog();
            //fillInCredentials();
            //clickLoginButton();

            if (loginChecker == null) {
                loginChecker = new Thread(() -> {
                    while(!Thread.currentThread().isInterrupted()) {
                        try {
                            checkIfLoggedIn();
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
                loginChecker.setDaemon(true);
                loginChecker.setName("Cam4 External browser login check");
                loginChecker.start();
            }
        }
    };

    private void checkIfLoggedIn() {
        try {
            browser.executeJavaScript("document.querySelector('a[id*=\"mainHeader_userMenuContent-logout\"]').text").thenAccept(r -> {
                LOG.debug("Result from browser is {}", r);
                // found the logout button, we can close the browser, the login was successful
                closeBrowser();
            }).exceptionally(ex -> null);
        } catch (IOException e) {
            LOG.error("Check, if logged in failed", e);
        }
    }

    private void clickAwayCookieAndAgeAcknowlegde() {
        if (!dialogsClicked) {
            try {
                browser.executeJavaScript("let ageButton = document.querySelector('button[id*=\"disclaimerWithAgeVerification_badge-agreeBtn\"]');"
                        + "if (ageButton) { ageButton.click(); }");
                browser.executeJavaScript("let cookieButton = document.querySelector('button[id*=\"cookieConsent_consentCookieBtn\"]');"
                        + "if (cookieButton) { cookieButton.click(); }");
                dialogsClicked = true;
            } catch (Exception e) {
                LOG.warn("Couldn't click on cookie and age acknowlegde buttons for Cam4", e);
            }
        }
    }

    private void safeCookies(JSONObject json) {
        if(json.has("cookies") && json.has("url")) {
            var url = json.getString("url");
            var cookiesFromBrowser = json.getJSONArray("cookies");
            for (var i = 0; i < cookiesFromBrowser.length(); i++) {
                var cookie = cookiesFromBrowser.getJSONObject(i);
                if (cookie.getString("domain").contains("cam4")) {
                    var domain = cookie.getString("domain");
                    if (domain.startsWith(".")) {
                        domain = domain.substring(1);
                    }
                    var c = createCookie(domain, cookie);
                    cookieJar.saveFromResponse(HttpUrl.parse(url), Collections.singletonList(c));
                    c = createCookie("cam4.com", cookie);
                    cookieJar.saveFromResponse(HttpUrl.parse(Cam4.BASE_URI), Collections.singletonList(c));
                }
            }
        }
    }

    private void openLoginDialog() {
        if (!loginDialogOpened) {
            try {
                browser.executeJavaScript("let loginButton = document.querySelector('button[id=\"loginButton\"]');"
                        + "console.log('loginButton', loginButton);"
                        + "if (loginButton) { loginButton.click(); }");
                loginDialogOpened = true;
            } catch (Exception e) {
                LOG.warn("Couldn't open login dialog for Cam4", e);
            }
        }
    }

    @SuppressWarnings("unused")
    private void fillInCredentials() {
        try {
            String username = Config.getInstance().getSettings().cam4Username;
            if (username != null && !username.trim().isEmpty()) {
                browser.executeJavaScript("document.querySelector('input[id*=\"loginFrom_usernameInput\"]').value = '" + username + "';");
            }
            String password = Config.getInstance().getSettings().cam4Password;
            if (password != null && !password.trim().isEmpty()) {
                password = password.replace("'", "\\'");
                browser.executeJavaScript("document.querySelector('input[id*=\"loginFrom_passwordInput\"]').value = '" + password + "');");
            }
        } catch (Exception e) {
            LOG.warn("Couldn't auto fill username and password for Cam4", e);
        }
    }

    @SuppressWarnings("unused")
    private void clickLoginButton() {
        try {
            browser.executeJavaScript("document.querySelector('button[id*=\"loginFrom_submitButton\"]').click();");
        } catch (Exception e) {
            LOG.warn("Couldn't click on login button for Cam4", e);
        }
    }

    private Cookie createCookie(String domain, JSONObject cookie) {
        Builder b = new Cookie.Builder()
                .path(cookie.getString("path"))
                .domain(domain)
                .name(cookie.getString("name"))
                .value(cookie.getString("value"))
                .expiresAt((long) cookie.optDouble("expirationDate"));
        if(cookie.optBoolean("hostOnly")) {
            b.hostOnlyDomain(domain);
        }
        if(cookie.optBoolean("httpOnly")) {
            b.httpOnly();
        }
        if(cookie.optBoolean("secure")) {
            b.secure();
        }
        return b.build();
    }

    private void closeBrowser() {
        try {
            Optional.ofNullable(loginChecker).ifPresent(Thread::interrupt);
            browser.close();
        } catch(IOException e) {
            LOG.error("Couldn't send close request to browser", e);
        }
    }
}
