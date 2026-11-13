package ctbrec.io;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import ctbrec.Config;
import ctbrec.Hmac;

public class UrlUtil {

    private UrlUtil() {}

    public static String addHmac(String url, Config config) throws MalformedURLException, InvalidKeyException, NoSuchAlgorithmException, IllegalStateException, UnsupportedEncodingException {
        URL u = new URL(url);
        String path = u.getPath();
        if (!config.getContextPath().isEmpty()) {
            path = path.substring(config.getContextPath().length());
        }
        byte[] key = config.getSettings().key;
        String hmac = Hmac.calculate(path, key);
        url = url + "?hmac=" + hmac;
        return url;
    }


}
