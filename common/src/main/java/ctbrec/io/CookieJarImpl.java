package ctbrec.io;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;

public class CookieJarImpl implements CookieJar {

    private static final Logger LOG = LoggerFactory.getLogger(CookieJarImpl.class);

    private final HashMap<String, List<Cookie>> cookieStore = new HashMap<>();

    @Override
    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
        String host = getDomain(url);
        List<Cookie> cookiesForUrl = cookieStore.get(host);
        if (cookiesForUrl != null) {
            cookiesForUrl = new ArrayList<>(cookiesForUrl);
            for (Iterator<Cookie> iterator = cookiesForUrl.iterator(); iterator.hasNext();) {
                Cookie oldCookie = iterator.next();
                String name = oldCookie.name();
                for (Cookie newCookie : cookies) {
                    if(newCookie.name().equalsIgnoreCase(name)) {
                        LOG.trace("Replacing cookie {} {} -> {} [{}]", oldCookie.name(), oldCookie.value(), newCookie.value(), oldCookie.domain());
                        iterator.remove();
                        break;
                    }
                }
            }
            cookiesForUrl.addAll(cookies);
            cookieStore.put(host, cookiesForUrl);
            LOG.trace("Adding cookie: {} for {}", cookiesForUrl, host);
        }
        else {
            cookieStore.put(host, cookies);
            LOG.trace("Storing cookie: {} for {}", cookies, host);
        }
    }

    @Override
    public List<Cookie> loadForRequest(HttpUrl url) {
        LOG.debug("Loading cookies for {}", url);
        String host = getDomain(url);
        List<Cookie> cookies = cookieStore.get(host);
        List<Cookie> result = new ArrayList<>();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                addCookieToResult(url, result, cookie);
            }
        }
        LOG.trace("Cookies for {}", url);
        Optional.ofNullable(result).ifPresent(cookiez -> cookiez.forEach(c -> LOG.trace("Cookies for {}: {}", url, c)));
        return result;
    }

    private void addCookieToResult(HttpUrl url, List<Cookie> result, Cookie cookie) {
        Optional<Cookie> cookieFromResults = getCookieFromCollection(result, cookie.name());
        if (cookieFromResults.isPresent()) {
            if (cookie.path().length() > cookieFromResults.get().path().length()) {
                String urlPath = String.join("/", url.pathSegments());
                if (urlPath.contains(cookie.path())) {
                    result.remove(cookieFromResults.get());
                    result.add(cookie);
                }
            }
        } else {
            result.add(cookie);
        }
    }

    public Optional<Cookie> getCookieFromCollection(Collection<Cookie> collection, String name) {
        for (Cookie cookie : collection) {
            if (Objects.equals(name, cookie.name())) {
                return Optional.of(cookie);
            }
        }
        return Optional.empty();
    }

    public Cookie getCookie(HttpUrl url, String name) {
        List<Cookie> cookies = loadForRequest(url);
        for (Cookie cookie : cookies) {
            if (Objects.equals(cookie.name(), name)) {
                return cookie;
            }
        }
        throw new NoSuchElementException("No cookie named " + name + " for " + url.host() + " available");
    }

    public void deleteCookie(HttpUrl url, String name) {
        List<Cookie> cookies = loadForRequest(url);
        for (Iterator<Cookie> iterator = cookies.iterator(); iterator.hasNext();) {
            Cookie cookie = iterator.next();
            if(Objects.equals(cookie.name(), name)) {
                iterator.remove();
                LOG.debug("Removed cookie \"{}\" for {}", name, url);
                return;
            }
        }
        throw new NoSuchElementException("No cookie named " + name + " for " + url.host() + " available");
    }

    private String getDomain(HttpUrl url) {
        return url.topPrivateDomain();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (Entry<String, List<Cookie>> entry : cookieStore.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        return sb.toString();
    }

    public Map<String, List<Cookie>> getCookies() {
        return cookieStore;
    }

    public void clear() {
        cookieStore.clear();
    }
}
