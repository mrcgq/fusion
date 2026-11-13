package ctbrec.sites.xlovecam;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class XloveCamHttpClient extends HttpClient {

    private static final Logger LOG = LoggerFactory.getLogger(XloveCamHttpClient.class);
    private static final Pattern CSRF_PATTERN = Pattern.compile("CSRFToken\\s*=\\s*\"(.*?)\";");
    private final Random rng = new Random();

    public XloveCamHttpClient(Config config) {
        super("xlovecam", config);
    }

    @Override
    public boolean login() throws IOException {
        String username = config.getSettings().xlovecamUsername;
        String csrfToken = getCsrfToken();
        JSONObject xlovecamConfig = getXlovecamConfig();
        String token = xlovecamConfig.getString("token");
        byte[] passwordKey = getPasswordKey(xlovecamConfig);
        byte[] encryptedPassword = encryptPassword(config.getSettings().xlovecamPassword, passwordKey);
        String base64EncryptedPassword = Base64.getEncoder().encodeToString(encryptedPassword);
        LOG.debug("csrf:{} token:{} key:{}", csrfToken, token, Arrays.toString(passwordKey));
        LOG.debug("encrypted password: {}", base64EncryptedPassword);

        long time = System.currentTimeMillis() / 1000;
        long rnd = rng.nextInt(100_000_000);
        String rndTokenString = token + ':' + Long.toString(rnd) + ":\u20ac\u2716\u21aa:" + time + ':' + username;
        long rndToken = fnv32a(rndTokenString.getBytes(UTF_8));

        RequestBody body = new FormBody.Builder() // @formatter:off
                .add("pseudo", username)
                .add("passwd", "")
                .add("keep", "1")
                .add("isLayer", "1")
                .add("token", token)
                .add("grecaptchaLoaded", "0")
                .add("extra[screen][width]", "1920")
                .add("extra[screen][height]", "1080")
                .add("extra[tmz]", "0")
                .add("extra[blng]", "en")
                .add("extra[clientTimezone]", "GMT")
                .add("extra[flash][installed]", "false")
                .add("extra[flash][raw]", "")
                .add("extra[flash][major]", "-1")
                .add("extra[flash][minor]", "-1")
                .add("extra[flash][revision]", "-1")
                .add("extra[flash][revisionStr]", "")
                .add("extra[rnd][rnd]", Long.toString(rnd))
                .add("extra[rnd][time]", Long.toString(time))
                .add("extra[rnd][token]", Integer.toString((int)rndToken))
                .add("csrf", csrfToken)
                .add("passwdEncoded", "b64:" + base64EncryptedPassword)
                .add("g-recaptcha-response", "")
                .build(); // @formatter:on

        String url = XloveCam.baseUrl + "/en/ajax/client/login2";
        Request req = new Request.Builder()
                .url(url)
                .method("POST", body)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, XloveCam.baseUrl + "/en/login")
                .header(ORIGIN, XloveCam.baseUrl)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(CONTENT_TYPE, FORM_ENCODED)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response resp = execute(req)) {
            if (resp.isSuccessful()) {
                String msg = resp.body().string();
                JSONObject json = new JSONObject(msg);
                LOG.debug(json.toString(2));
                return json.optBoolean("success");
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    private long fnv32a(byte[] a) {
        long b = 2166136261l;
        for (int d = 0; d < a.length; d++) {
            b ^= (a[d] & 0xFF);
            b += (b << 1) + (b << 4) + (b << 7) + (b << 8) + (b << 24) & 4294967295l;
        }
        return b >>> 0;
    }

    private byte[] encryptPassword(String xlovecamPassword, byte[] passwordKey) {
        byte[] password = xlovecamPassword.getBytes(UTF_8);
        byte d = (byte) password.length;
        byte[] c = new byte[password.length + rng.nextInt(20)];
        System.arraycopy(password, 0, c, 0, password.length);
        for (int i = password.length; i < c.length; i++) {
            c[i] = (byte)rng.nextInt(255);
        }

        byte[] b = new byte[c.length + 1];
        b[0] = (byte)(d ^ (passwordKey[0] & 0xFF));

        d = 1;
        for (int e = 0; e < c.length; e++) {
            b[e+1] = (byte)(c[e] ^ passwordKey[d]);
            d++;
            if(d >= passwordKey.length) {
                d = 0;
            }
        }
        return b;
    }

    private byte[] getPasswordKey(JSONObject config) {
        String passwordKeyString = config.getString("passwordKey");
        LOG.debug(passwordKeyString);
        String[] numbers = passwordKeyString.split(",");
        byte[] passwordKey = new byte[numbers.length];
        for (int i = 0; i < numbers.length; i++) {
            passwordKey[i] = (byte)(Integer.parseInt(numbers[i]) & 0xFF);
        }
        return passwordKey;
    }

    private String getCsrfToken() throws IOException {
        Request req = new Request.Builder()
                .url(XloveCam.baseUrl)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .build();
        try (Response resp = execute(req)) {
            if (resp.isSuccessful()) {
                String body = resp.body().string();
                Matcher m = CSRF_PATTERN.matcher(body);
                if (m.find()) {
                    return m.group(1);
                } else {
                    throw new IOException("CSRF token not found in landing page");
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }

    private JSONObject getXlovecamConfig() throws IOException {
        String url = XloveCam.baseUrl + "/en/popup/login";
        LOG.debug("Calling {}", url);
        RequestBody body = new FormBody.Builder()
                .add("referrer", "https://www.xlovecam.com/en/")
                .add("referrer_is_layer", "0")
                .add("referrer_model_id", "")
                .add("referrer_model_name", "")
                .build();
        Request req = new Request.Builder()
                .url(url)
                .method("POST", body)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, XloveCam.baseUrl)
                .header(ORIGIN, XloveCam.baseUrl)
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(CONTENT_TYPE, FORM_ENCODED)
                .header(X_REQUESTED_WITH, XML_HTTP_REQUEST)
                .build();
        try (Response resp = execute(req)) {
            if (resp.isSuccessful()) {
                String msg = resp.body().string();
                JSONObject json = new JSONObject(msg);
                if (json.has("config")) {
                    return json.getJSONObject("config");
                } else {
                    throw new IOException("JSON doesn't contain \"config\":\n" + json.toString(2));
                }
            } else {
                throw new HttpException(resp.code(), resp.message());
            }
        }
    }
}
