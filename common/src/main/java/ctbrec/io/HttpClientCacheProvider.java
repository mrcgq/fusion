package ctbrec.io;

import ctbrec.Config;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Cache;

import java.io.File;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
public class HttpClientCacheProvider {

    private static final Lock lock = new ReentrantLock();
    private static HttpClientCacheProvider provider;

    private Cache cache;

    private HttpClientCacheProvider(Config config) {
        File configDir = config.getConfigDir();
        File cacheDir = new File(configDir, "cache");
        long cacheSize = (long) config.getSettings().thumbCacheSize * 1024 * 1024;
        try {
            cache = new Cache(cacheDir, cacheSize);
        } catch (Exception ex) {
            log.error("Could not create HTTP client cache", ex);
        }
    }

    private Cache getCache() {
        return cache;
    }

    public static Cache getCache(Config config) {
        lock.lock();
        try {
            if (provider == null) {
                provider = new HttpClientCacheProvider(config);
            }
            return provider.getCache();
        } finally {
            lock.unlock();
        }
    }

}
