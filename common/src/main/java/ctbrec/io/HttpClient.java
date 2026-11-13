package ctbrec.io;

import com.fasterxml.jackson.core.type.TypeReference;
import ctbrec.Config;
import ctbrec.LoggingInterceptor;
import ctbrec.Settings.ProxyType;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.io.json.dto.CookieDto;
import ctbrec.io.json.mapper.CookieMapper;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import okhttp3.OkHttpClient.Builder;
import org.mapstruct.factory.Mappers;

import javax.net.ssl.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.nio.file.Files;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.NumberFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.time.*;
import java.util.Optional;

import static ctbrec.io.HttpConstants.ACCEPT_ENCODING_GZIP;
import static ctbrec.io.HttpConstants.CONTENT_ENCODING;
import static java.nio.charset.StandardCharsets.UTF_8;


@Slf4j
public abstract class HttpClient {
    @Getter
    private static final ConnectionPool GLOBAL_HTTP_CONN_POOL = new ConnectionPool(256, 2, TimeUnit.MINUTES);

    @Getter
    protected CookieJarImpl cookieJar;
    protected OkHttpClient client;
    protected Cache cache;
    protected Config config;
    protected boolean loggedIn = false;
    protected long cacheSize;
    protected int cacheLifeTime = 600;
    private final String name;
    
    protected final FlaresolverrClient flaresolverr;
    // a lock to prevent multiple requests from 
    ReentrantReadWriteLock cookieRefreshLock = new ReentrantReadWriteLock();
    AtomicInteger cookieErrorCounter = new AtomicInteger(0);

    protected HttpClient(String name, Config config) {
        this.name = name;
        this.config = config;
        cookieJar = createCookieJar();
        reconfigure();
                
        if (!config.getSettings().flaresolverr.useForDomains.isEmpty()) {
            flaresolverr = new FlaresolverrClient(config.getSettings().flaresolverr.apiUrl, config.getSettings().flaresolverr.timeoutInMillis);
            
            // try {
            //     flaresolverr.createSession("ctbrec").get();
            // } catch (InterruptedException e) {
            //     Thread.currentThread().interrupt();
            // } catch (Exception e) {
            //     log.error("Error starting Flaresolverr session", e);
            // }
        } else {
            flaresolverr = null;
        }
    }

    protected CookieJarImpl createCookieJar() {
        return new CookieJarImpl();
    }

    private void loadProxySettings() {
        ProxyType proxyType = config.getSettings().proxyType;
        switch (proxyType) {
            case HTTP:
                System.setProperty(ProxyConstants.HTTP_PROXY_HOST, config.getSettings().proxyHost);
                System.setProperty(ProxyConstants.HTTP_PROXY_PORT, config.getSettings().proxyPort);
                System.setProperty(ProxyConstants.HTTPS_PROXY_HOST, config.getSettings().proxyHost);
                System.setProperty(ProxyConstants.HTTPS_PROXY_PORT, config.getSettings().proxyPort);
                if (config.getSettings().proxyUser != null && !config.getSettings().proxyUser.isEmpty()) {
                    String username = config.getSettings().proxyUser;
                    String password = config.getSettings().proxyPassword;
                    System.setProperty(ProxyConstants.HTTP_PROXY_USER, username);
                    System.setProperty(ProxyConstants.HTTP_PROXY_PASSWORD, password);
                }
                break;
            case SOCKS4:
                System.setProperty(ProxyConstants.SOCKS_PROXY_VERSION, "4");
                System.setProperty(ProxyConstants.SOCKS_PROXY_HOST, config.getSettings().proxyHost);
                System.setProperty(ProxyConstants.SOCKS_PROXY_PORT, config.getSettings().proxyPort);
                break;
            case SOCKS5:
                System.setProperty(ProxyConstants.SOCKS_PROXY_VERSION, "5");
                System.setProperty(ProxyConstants.SOCKS_PROXY_HOST, config.getSettings().proxyHost);
                System.setProperty(ProxyConstants.SOCKS_PROXY_PORT, config.getSettings().proxyPort);
                if (config.getSettings().proxyUser != null && !config.getSettings().proxyUser.isEmpty()) {
                    String username = config.getSettings().proxyUser;
                    String password = config.getSettings().proxyPassword;
                    Authenticator.setDefault(new SocksProxyAuth(username, password));
                }
                break;
            case DIRECT:
            default:
                System.clearProperty(ProxyConstants.HTTP_PROXY_HOST);
                System.clearProperty(ProxyConstants.HTTP_PROXY_PORT);
                System.clearProperty(ProxyConstants.HTTPS_PROXY_HOST);
                System.clearProperty(ProxyConstants.HTTPS_PROXY_PORT);
                System.clearProperty(ProxyConstants.SOCKS_PROXY_VERSION);
                System.clearProperty(ProxyConstants.SOCKS_PROXY_HOST);
                System.clearProperty(ProxyConstants.SOCKS_PROXY_PORT);
                System.clearProperty(ProxyConstants.JAVA_NET_SOCKS_USERNAME);
                System.clearProperty(ProxyConstants.JAVA_NET_SOCKS_PASSWORD);
                System.clearProperty(ProxyConstants.HTTP_PROXY_USER);
                System.clearProperty(ProxyConstants.HTTP_PROXY_PASSWORD);
                break;
        }
    }

    private Response execute(Call call) throws IOException {   
        Response resp;
        
        try {
            cookieRefreshLock.readLock().lock();
            resp = call.execute();
        } finally {
            cookieRefreshLock.readLock().unlock();
        }
        
        // try to solve the cloudflare challenge if we got one (clearance cookie expired, update it)
        if (resp.code() == 403 && config.getSettings().flaresolverr.useForDomains.contains(call.request().url().host())) {
            resp = refreshCookiesAndRetry(call.request(), resp);
        }
        
        return resp;
    }

    public Response execute(Request req) throws IOException {   
        return execute(client.newCall(req));
    }

    public Response execute(Request request, int timeoutInMillis) throws IOException {
            return execute(client.newBuilder() //
                            .connectTimeout(timeoutInMillis, TimeUnit.MILLISECONDS) //
                            .readTimeout(timeoutInMillis, TimeUnit.MILLISECONDS).build() //
                            .newCall(request));
    }

    public Response executeWithCache(Request req) throws IOException {
        log.trace("Cached request for {}", req.url());
        if (Objects.nonNull(cache)) {
            log.trace("Cache hit ratio {}/{} = {}", cache.hitCount(), cache.requestCount(), NumberFormat.getPercentInstance().format(cache.hitCount() / (double) cache.requestCount()));
        }
        if (cacheSize > 0 && Objects.nonNull(cache)) {
            Request r = req.newBuilder()
                    .cacheControl(new CacheControl.Builder().maxAge(cacheLifeTime, TimeUnit.SECONDS).build())
                    .build();
            return execute(r);
        } else {
            return execute(req);
        }
    }
    
    private Response refreshCookiesAndRetry(Request req, Response origResp) throws IOException {
        log.debug("403 received from {}. Trying to refresh cookies with Flaresolverr", req.url().host());
        
        try {
            cookieRefreshLock.writeLock().lock();            
            
            // we need to prevent repeated challenge requests from multiple threads, so we check if the clearance cookie needs updating
            // maybe this can be done with some syncronization primitive, or maybe an expiresAt() check is enough
            var cookie = Optional
                .ofNullable(cookieJar.getCookies().get(req.url().topPrivateDomain()))
                .flatMap(x -> cookieJar.getCookieFromCollection(x, "cf_clearance"));
                
            var cookieExpired = cookie.map(c -> 
                Instant.ofEpochMilli(c.expiresAt()).isBefore(Instant.now())  // by time
                || req.headers("Cookie").stream().anyMatch(headerCookie -> headerCookie.contains(c.value()))  // we got 403 with current cookie present
            ).orElse(true);
                
            if (cookieExpired || cookieErrorCounter.incrementAndGet() >= 5) {
                cookieErrorCounter.set(0);
                
                var apiResponse = flaresolverr.getCookies(req.url().toString()).get();
                if (apiResponse.getStatus().equals("ok")) {
                    // update user agent. It should be the same for all sites, assuming we use the same api address every time
                    if (!config.getSettings().flaresolverr.userAgent.equals(apiResponse.getUserAgent())) {
                        config.getSettings().flaresolverr.userAgent = apiResponse.getUserAgent();
                        config.save();
                    }
                    
                    cookieJar.saveFromResponse(req.url(), apiResponse.getCookies());    
                    persistCookies();  
                    log.debug("Cookies successfully refreshed with Flaresolverr in {}", Duration.between(apiResponse.getStartTimestamp(), apiResponse.getEndTimestamp()));
                } else {
                    log.debug("Unsuccessful attempt to refresh cookies. Response from Flaresolverr: {}", apiResponse);
                    return origResp;
                }
            } else {
                log.debug("Looks like the cookies were refreshed already, skipping refreshing");
            }
        } catch (Exception e) {
            log.warn("Error refreshing cookies with Flaresolverr", e);
            return origResp;
        } finally {
            cookieRefreshLock.writeLock().unlock();
        }
        
        origResp.close();
        
        return execute(req);
    }

    public abstract boolean login() throws IOException;

    public void reconfigure() {
        loadProxySettings();
        loadCookies();
        cacheSize = (long) config.getSettings().thumbCacheSize * 1024 * 1024;

        Builder builder = new OkHttpClient.Builder()
                .cookieJar(cookieJar)
                .connectionPool(GLOBAL_HTTP_CONN_POOL)
                .connectTimeout(config.getSettings().httpTimeout, TimeUnit.MILLISECONDS)
                .readTimeout(config.getSettings().httpTimeout, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(new LoggingInterceptor());

        if (cacheSize > 0) {
            cache = HttpClientCacheProvider.getCache(config);
            if (cache != null) {
                builder.cache(cache);
            }
        }

        ProxyType proxyType = config.getSettings().proxyType;
        if (proxyType == ProxyType.HTTP) {
            String username = config.getSettings().proxyUser;
            String password = config.getSettings().proxyPassword;
            if (username != null && !username.isEmpty()) {
                builder.proxyAuthenticator(createHttpProxyAuthenticator(username, password));
            }
        }

        // if transport layer security (TLS) is switched on, accept the self-signed cert from the server
        if (config.getSettings().transportLayerSecurity) {
            acceptAllTlsCerts(builder);
        }

        client = builder.build();
        client.dispatcher().setMaxRequests(config.getSettings().httpClientMaxRequests);
        client.dispatcher().setMaxRequestsPerHost(config.getSettings().httpClientMaxRequestsPerHost);
    }

    /**
     * This is a very simple and insecure solution to accept the self-signed cert from
     * the server. The side effect is, that certificates from other servers are neither checked!
     * TODO Delegate to the default trustmanager, if it is not the self-signed cert
     */
    private void acceptAllTlsCerts(Builder builder) {
        X509TrustManager x509TrustManager = new X509TrustManager() {
            @Override
            public X509Certificate[] getAcceptedIssuers() {
                X509Certificate[] x509Certificates = new X509Certificate[0];
                return x509Certificates;
            }

            @Override
            public void checkServerTrusted(final X509Certificate[] chain, final String authType) { /* noop*/ }

            @Override
            public void checkClientTrusted(final X509Certificate[] chain, final String authType) { /* noop*/ }
        };

        try {
            final TrustManager[] trustManagers = new TrustManager[]{x509TrustManager};
            final String PROTOCOL = "TLSv1.2";
            SSLContext sslContext = SSLContext.getInstance(PROTOCOL);
            KeyManager[] keyManagers = null;
            SecureRandom secureRandom = new SecureRandom();
            sslContext.init(keyManagers, trustManagers, secureRandom);
            SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();
            builder.sslSocketFactory(sslSocketFactory, x509TrustManager);
            builder.hostnameVerifier((hostname, sslSession) -> true);
        } catch (KeyManagementException | NoSuchAlgorithmException e) {
            log.error("Couldn't install trust managers for TLS connections");
        }
    }

    public void shutdown() {
        persistCookies();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
    }

    protected void persistCookies() {
        try {
            List<CookieContainer> containers = new ArrayList<>();
            cookieJar.getCookies().forEach((domain, cookieList) -> {
                CookieContainer cookies = new CookieContainer();
                cookies.setDomain(domain);
                List<CookieDto> dtos = cookieList.stream().map(Mappers.getMapper(CookieMapper.class)::toDto).toList();
                cookies.setCookies(dtos);
                containers.add(cookies);
            });
            String json = ObjectMapperFactory.getMapper().writeValueAsString(containers);
            File cookieFile = new File(config.getConfigDir(), "cookies-" + name + ".json");
            Files.writeString(cookieFile.toPath(), json);
        } catch (Exception e) {
            log.error("Couldn't persist cookies for {}", name, e);
        }
    }

    protected void loadCookies() {
        try {
            File cookieFile = new File(config.getConfigDir(), "cookies-" + name + ".json");
            if (!cookieFile.exists()) {
                return;
            }
            String json = Files.readString(cookieFile.toPath());
            Map<String, List<Cookie>> cookies = cookieJar.getCookies();
            List<CookieContainer> fromJson = ObjectMapperFactory.getMapper().readValue(json, new TypeReference<>() {
            });
            for (CookieContainer container : fromJson) {
                List<Cookie> filteredCookies = container.getCookies().stream()
                        .filter(c -> !Objects.equals("deleted", c.getValue()))
                        .map(Mappers.getMapper(CookieMapper.class)::toCookie)
                        .collect(Collectors.toList());
                cookies.put(container.getDomain(), filteredCookies);
            }
        } catch (Exception e) {
            log.error("Couldn't load cookies for {}", name, e);
        }
    }

    @Data
    public static class CookieContainer {
        private String domain;
        private List<CookieDto> cookies;
    }

    private okhttp3.Authenticator createHttpProxyAuthenticator(String username, String password) {
        return (route, response) -> {
            String credential = Credentials.basic(username, password);
            return response.request().newBuilder().header("Proxy-Authorization", credential).build();
        };
    }

    public static class SocksProxyAuth extends Authenticator {
        private final PasswordAuthentication auth;

        private SocksProxyAuth(String user, String password) {
            auth = new PasswordAuthentication(user, password == null ? new char[]{} : password.toCharArray());
        }

        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            return auth;
        }
    }

    public void logout() {
        getCookieJar().clear();
        loggedIn = false;
    }

    public WebSocket newWebSocket(Request request, WebSocketListener l) {
        return client.newWebSocket(request, l);
    }

    public List<Cookie> getCookiesByName(String... names) {
        List<Cookie> result = new ArrayList<>();
        Map<String, List<Cookie>> cookies = getCookieJar().getCookies();
        for (List<Cookie> cookieList : cookies.values()) {
            for (Cookie cookie : cookieList) {
                for (String cookieName : names) {
                    if (Objects.equals(cookieName, cookie.name())) {
                        result.add(cookie);
                    }
                }
            }
        }
        return result;
    }

    public static String bodyToJsonObject(Response response) {
        return Optional.ofNullable(response.body()).map(b -> {
            try {
                return b.string();
            } catch (IOException e) {
                return "{}";
            }
        }).orElse("{}");
    }

    public static String bodyToJsonArray(Response response) {
        return Optional.ofNullable(response.body()).map(b -> {
            try {
                return b.string();
            } catch (IOException e) {
                return "[]";
            }
        }).orElse("[]");
    }


    public static String gunzipBody(Response response) throws IOException {
        if (Objects.equals(response.header(CONTENT_ENCODING), ACCEPT_ENCODING_GZIP)) {
            GZIPInputStream gzipIn = new GZIPInputStream(Objects.requireNonNull(response.body()).byteStream());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int len;
            while ((len = gzipIn.read(b)) >= 0) {
                bos.write(b, 0, len);
            }
            return bos.toString(UTF_8);
        } else {
            return Objects.requireNonNull(response.body()).string();
        }
    }

    public void clearCookies() {
        logout();
    }

    private static class ProxyConstants {
        public static final String HTTP_PROXY_HOST = "http.proxyHost";
        public static final String HTTP_PROXY_PORT = "http.proxyPort";
        public static final String HTTPS_PROXY_HOST = "https.proxyHost";
        public static final String HTTPS_PROXY_PORT = "https.proxyPort";
        public static final String HTTP_PROXY_USER = "https.proxyUser";
        public static final String HTTP_PROXY_PASSWORD = "https.proxyPassword";

        public static final String SOCKS_PROXY_HOST = "socksProxyHost";
        public static final String SOCKS_PROXY_PORT = "socksProxyPort";
        public static final String SOCKS_PROXY_VERSION = "socksProxyVersion";
        public static final String JAVA_NET_SOCKS_USERNAME = "java.net.socks.username";
        public static final String JAVA_NET_SOCKS_PASSWORD = "java.net.socks.password";
    }
    
    // overridable default user agent (used for Flaresolverr)
    public String getEffectiveUserAgent() {
        return config.getSettings().httpUserAgent;
    }
}
