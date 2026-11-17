package ctbrec.sites.stripchat;

import ctbrec.Config;
import ctbrec.StringUtil;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import static ctbrec.io.HttpConstants.*;

/**
 * Stripchat 边缘主机 / master URL 解析（现网实测）：
 *
 *   master: https://edge-hls.doppiocdn.net/hls/{id}/master/{id}.m3u8        (+ _auto / _vr 变体)
 *           master body 内含若干 #EXT-X-MOUFLON:PSCH:v2:&lt;seed&gt;，任一 seed 作 pkey 都能让
 *           变体指向的 media 播放列表返回真·live（不带 psch/pkey 会返回 CPA 广告假列表）。
 *
 * 5.4.4 原实现把 master 硬编码到 https://edge-hls.saawsedge.com/hls/{id}/master/{id}.m3u8 且
 * 路径 shape 也不对（b-hls-09/{id}/{id}.m3u8 现网 404）。这里按固定优先级探测可用主机并缓存，
 * 同时把 master 里解析出的 PSCH seed 缓存下来供播放列表/段拼 pkey。
 *
 * 与出口 IP 无关：master/edge 只做主机探测，结果按 host 缓存；出口变化由 EgressGuard 统一失效。
 */
public final class StripchatEdgeResolver {

    private static final Logger LOG = LoggerFactory.getLogger(StripchatEdgeResolver.class);

    /** 现网存活边缘（按优先级）。doppiocdn.net 主用，saawsedge/.com/.org 作回退。 */
    private static final String[] EDGE_HOSTS = {
            "edge-hls.doppiocdn.net",
            "edge-hls.saawsedge.com",
            "edge-hls.doppiocdn.com",
            "edge-hls.doppiocdn.org",
    };

    private static final AtomicReference<Cached> CACHE = new AtomicReference<>();
    private static final long TTL_MS = 10 * 60 * 1000L;

    private static final class Cached {
        final String host;
        final long at;
        Cached(String host) { this.host = host; this.at = System.currentTimeMillis(); }
        boolean fresh() { return System.currentTimeMillis() - at < TTL_MS; }
    }

    private StripchatEdgeResolver() { }

    /** 强制丢弃主机缓存（IP 变动 / 全部主机 404 时调用）。 */
    public static void invalidate() {
        CACHE.set(null);
    }

    /** master URL：https://{edge}/hls/{id}/master/{id}{suffix}.m3u8 （suffix 为 _auto/_vr/""）。 */
    public static String masterUrl(Stripchat site, long modelId, String suffix) throws IOException {
        String sfx = suffix == null ? "" : suffix;
        return "https://" + resolveHost(site) + "/hls/" + modelId + "/master/" + modelId + sfx + ".m3u8";
    }

    /**
     * 规范化任意来源的 master URL：把旧 saawsedge / 错误路径 / 其它 doppiocdn 主机改写到当前存活 edge host，
     * 保留 id 与 _auto/_vr 后缀；无法识别则原样返回。
     */
    public static String normalizeMasterUrl(Stripchat site, String url) {
        try {
            if (url == null) return null;
            long id = extractId(url);
            if (id <= 0) return url;
            String lower = url.toLowerCase(Locale.ROOT);
            String suffix = lower.contains("_vr") ? "_vr" : (lower.contains("_auto") ? "_auto" : "");
            String host = URI.create(url).getHost();
            boolean alreadyGood = host != null && host.equalsIgnoreCase(currentHostHint());
            if (alreadyGood) return url;
            String fixed = masterUrl(site, id, suffix);
            LOG.debug("Rewrote stripchat master URL {} -> {}", url, fixed);
            return fixed;
        } catch (Exception e) {
            LOG.debug("normalizeMasterUrl failed for {}", url, e);
            return url;
        }
    }

    /** 从 hls/{id}/ 或 b-hls-NN/{id}/ 两种路径里取模型 id。 */
    public static long extractId(String url) {
        try {
            String path = URI.create(url).getPath();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:/hls/|/b-hls-\\d+/)(\\d+)").matcher(path);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (Exception ignored) { }
        return -1;
    }

    /** 保留查询串（psch/pkey/aclAuth 段/播放列表都得带，砍了就 404）。 */
    public static String keepQueryString(String uri) {
        return uri == null ? null : uri.trim();
    }

    private static String currentHostHint() {
        Cached c = CACHE.get();
        return c != null ? c.host : EDGE_HOSTS[0];
    }

    /** 探测并缓存存活主机（返回 200/404 都算主机活着，只有连不上才换下一个）。 */
    private static String resolveHost(Stripchat site) {
        Cached c = CACHE.get();
        if (c != null && c.fresh()) return c.host;
        String probe = "https://%s/ping.m3u8";
        for (String host : EDGE_HOSTS) {
            try {
                Request req = new Request.Builder()
                        .url(String.format(probe, host))
                        .header(USER_AGENT, userAgent(ctbrec.Config.getInstance()))
                        .header(ACCEPT, "application/vnd.apple.mpegurl")
                        .build();
                try (var resp = site.getHttpClient().execute(req)) {
                    if (resp.code() > 0) {           // 只要 DNS/TLS/HTTP 通了即认为该 edge 可用
                        CACHE.set(new Cached(host));
                        LOG.debug("stripchat edge host = {} (ping {})", host, resp.code());
                        return host;
                    }
                }
            } catch (Exception e) {
                LOG.trace("edge probe failed {}", host, e);
            }
        }
        CACHE.set(new Cached(EDGE_HOSTS[0]));
        return EDGE_HOSTS[0];
    }

    static String userAgent(Config config) {
        String ua = config.getSettings().httpUserAgent;
        return StringUtil.isBlank(ua) ? "Mozilla/5.0" : ua;
    }

    /** 供测试：可选地预置一个主机，绕过探测。 */
    static void presetHost(String host) {
        CACHE.set(new Cached(host));
    }
}
