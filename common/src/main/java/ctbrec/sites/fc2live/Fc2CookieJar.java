package ctbrec.sites.fc2live;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import ctbrec.io.CookieJarImpl;
import okhttp3.Cookie;
import okhttp3.HttpUrl;

public class Fc2CookieJar extends CookieJarImpl {

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        List<Cookie> sanitizedCookies = new ArrayList<>(cookies);
        for (Iterator<Cookie> iterator = sanitizedCookies.iterator(); iterator.hasNext();) {
            Cookie cookie = iterator.next();
            if(cookie.value().equalsIgnoreCase("deleted")) {
                // ignore and remove from list
                iterator.remove();
            }
        }
        super.saveFromResponse(url, sanitizedCookies);
    }
}
