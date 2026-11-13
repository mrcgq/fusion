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

import static ctbrec.io.HttpConstants.*;

/**
 * Stripchat 边缘节点解析（PSCH v2 通路）。
 *
 * 5.4.4 原始实现把 master URL 硬编码为 https://edge-hls.saawsedge.com/hls/{id}/master/{id}.m3u8，
 * 现网该域已下线；真实播放列表在 media-hls.doppiocdn.net（及 edge-hls.doppiocdn.net/.com）的
 * b-hls-NN/{id}/{id}.m3u8，且必须带 psch=v2&pkey=<XOR Key (v2)> 才返回 200（无 pkey = 404）。
 *
 * 本类按固定优先级探测边缘主机，缓存结果，并把 Settings 中的 MOUFLON 解密 key（xorKeyV2，
 * 为空时回退 v1Key1/v1Key2）作为 pkey 附加到 master 与 media playlist URL 上。
 */
public final class StripchatEdgeResolver {
    private static final Logger LOG = LoggerFactory.getLogger(StripchatEdgeResolver.class);

    private static final String[] EDGE_HOSTS = {
        "media-hls.doppiocdn.net",
        "edge-hls.doppiocdn.net",
        "edge-hls.doppiocdn.com",
    };

    private static volatile String cachedHost = null;
    private static volatile long cachedAt = 0L;
    private static final long TTL_MS = 15 * 60 * 1000L;

    private StripchatEdgeResolver() { }

    /** pkey = Settings 的 XOR Key (v2)，留空回退 V1 Key 1 / V1 Key 2。 */
    public static String pkey(Config config) {
        String k = config.getSettings().xorKeyV2;
        if (StringUtil.isBlank(k)) k = config.getSettings().v1Key1;
        if (StringUtil.isBlank(k)) k = config.getSettings().v1Key2;
        return k == null ? "" : k.trim();
    }

    /** MOUFLON PSCH v2 seed（16 位），来自 master 的 #EXT-X-MOUFLON:PSCH:v2:<seed>。 */
    public static String pschSeed(String masterBody) {
        if (masterBody == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("#EXT-X-MOUFLON:PSCH:v2:(\\S+)").matcher(masterBody);
        return m.find() ? m.group(1) : "";
    }

    /** master URL：{host}/b-hls-09/{id}{vr}/{id}{vr}.m3u8?psch=v2&pkey=... */
    public static String masterUrl(Stripchat site, long modelId, boolean vr) throws IOException {
        String host = resolveHost(site, modelId, vr);
        String vrSfx = vr ? "_vr" : "";
        String pk = pkey(site.getConfig());
        return "https://" + host + "/b-hls-09/" + modelId + vrSfx + "/" + modelId + vrSfx + ".m3u8"
                + "?psch=v2" + (pk.isEmpty() ? "" : "&pkey=" + pk);
    }

    /**
     * 若原始 URL 是旧 saawsedge master 或缺 psch/pkey，就重建为现网 URL；否则原样返回。
     */
    public static String normalizeMasterUrl(Stripchat site, String url) {
        try {
            if (url == null) return null;
            String lower = url.toLowerCase(Locale.ROOT);
            boolean stale = lower.contains("saawsedge.com");
            boolean missingPsch = lower.contains("/hls/") && !lower.contains("psch=");
            if (!stale && !missingPsch) return url;
            long id = extractId(url);
            if (id <= 0) return url;
            boolean vr = lower.contains("_vr");
            String fixed = masterUrl(site, id, vr);
            LOG.debug("Rewrote stripchat master URL {} -> {}", url, fixed);
            return fixed;
        } catch (Exception e) {
            LOG.debug("normalizeMasterUrl failed for {}", url, e);
            return url;
        }
    }

    /** 从 hls/{id}/ 或 b-hls-NN/{id}/ 两种路径抽模型 id。 */
    public static long extractId(String url) {
        try {
            String path = URI.create(url).getPath();
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(?:/hls/|/b-hls-\\d+/)(\\d+)").matcher(path);
            if (m.find()) return Long.parseLong(m.group(1));
        } catch (Exception ignored) { }
        return -1;
    }

    /**
     * 保留段 URI 的全部查询参数（5.4.4 会截掉 '?' 之后的内容，现网截掉必 404）。
     */
    public static String keepQueryString(String uri) {
        return uri == null ? null : uri.trim();
    }

    /**
     * MOUFLON PSCH v2 段重排（还原为播放顺序）：
     * 行尾 _<epoch> 升序；同 epoch 按 partN 升序；再按 seed 首字符奇偶重排。
     */
    public static List<String> demanglePschSegments(List<String> uris, String seed) {
        if (uris == null || uris.size() < 2) return uris == null ? new ArrayList<>() : new ArrayList<>(uris);
        List<String> sorted = new ArrayList<>(uris);
        sorted.sort((a, b) -> {
            long ea = epochOf(a), eb = epochOf(b);
            if (ea != eb) return Long.compare(ea, eb);
            return Integer.compare(partOf(a), partOf(b));
        });
        List<String> out = new ArrayList<>(sorted.size());
        for (int i = 0; i < sorted.size(); i++) out.add(null);
        List<String> even = new ArrayList<>();
        List<String> odd = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (((i + seedOffset(seed)) & 1) == 0) even.add(sorted.get(i));
            else odd.add(sorted.get(i));
        }
        java.util.Collections.reverse(odd);
        int ei = 0, oi = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (((i + seedOffset(seed)) & 1) == 0) out.set(i, even.get(ei++));
            else out.set(i, odd.get(oi++));
        }
        return out;
    }

    /** seed 首字符 ASCII % 2 —— 决定偶/奇起始位。 */
    public static int seedOffset(String seed) {
        if (seed == null || seed.isEmpty()) return 0;
        return seed.charAt(0) % 2;
    }

    private static long epochOf(String uri) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("_(\\d{6,})_part\\d+\\.[a-z0-9]+(?:\\?|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(uri);
        if (m.find()) return Long.parseLong(m.group(1));
        return 0L;
    }

    private static int partOf(String uri) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("_part(\\d+)\\.[a-z0-9]+(?:\\?|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(uri);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    private static String resolveHost(Stripchat site, long modelId, boolean vr) {
        long now = System.currentTimeMillis();
        String h = cachedHost;
        if (h != null && now - cachedAt < TTL_MS) return h;
        String pk = pkey(site.getConfig());
        String vrSfx = vr ? "_vr" : "";
        String host = EDGE_HOSTS[0];
        for (String cand : EDGE_HOSTS) {
            String url = "https://" + cand + "/b-hls-09/" + modelId + vrSfx + "/" + modelId + vrSfx + ".m3u8"
                    + "?psch=v2" + (pk.isEmpty() ? "" : "&pkey=" + pk);
            try {
                Request req = new Request.Builder()
                        .url(url)
                        .header(USER_AGENT, site.getConfig().getSettings().httpUserAgent)
                        .header(ACCEPT, "application/vnd.apple.mpegurl")
                        .build();
                try (var response = site.getHttpClient().execute(req)) {
                    if (response.isSuccessful()) {
                        host = cand;
                        break;
                    }
                }
            } catch (Exception e) {
                LOG.trace("edge probe failed {}", cand, e);
            }
        }
        cachedHost = host;
        cachedAt = now;
        LOG.debug("stripchat edge host = {}", host);
        return host;
    }
}
