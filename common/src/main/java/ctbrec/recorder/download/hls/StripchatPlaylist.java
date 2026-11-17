package ctbrec.recorder.download.hls;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.sites.stripchat.mouflon.Mouflon;
import ctbrec.sites.stripchat.mouflon.MouflonSettings;

import java.net.URI;

/**
 * MOUFLON PSCH v2 播放列表重写（Stripchat 专用，其它站点原样返回）。
 *
 * 现网（media-hls.doppiocdn.net/b-hls-NN/{id}/{id}.m3u8）实测形态：
 *   #EXT-X-MOUFLON:PSCH:v2:&lt;seed&gt;                      (master 里一堆，任一作 pkey 即可出真·live)
 *   #EXTINF:2.000,
 *   #EXT-X-MOUFLON:URI:https://…/{id}_{seq}_&lt;hash&gt;_{epoch}.mp4   &lt;- hash 是 XOR 混淆的
 *   https://…/media.mp4                                        &lt;- 占位（直接抓必 404 / 落到 CPA 假列表）
 *
 * 因此这里在解析前：
 *   1) 对每条 #EXT-X-MOUFLON:URI 的段名 hash 做 {@link Mouflon#decryptV2Url}（sha256(xorKey)[:16] 逐字节异或）
 *      还原真实段名，再替换紧随其后的 media.mp4 占位段行；
 *   2) 播放列表 URL 补 psch=v2&pkey（否则拿到的是 CPA 广告 VOD 假列表）；
 *   3) 真实段 URL 继承播放列表的 psch/pkey（缺则 404）。
 */
public final class StripchatPlaylist {

    private StripchatPlaylist() { }

    public static boolean isStripchatModel(Model model) {
        try {
            return model != null && "stripchat".equalsIgnoreCase(model.getSite().getName());
        } catch (Exception e) {
            return false;
        }
    }

    /** 媒体播放列表 URL 补齐 psch=v2&pkey（现网任一 PSCH seed 作 pkey 都能出真·live 列表）。 */
    public static String rewritePlaylistUrl(String url, Config config, Model model) {
        if (!isStripchatModel(model) || url == null || url.isEmpty()) return url;
        String lower = url.toLowerCase();
        if (lower.contains("psch=")) return url;
        String pk = pkey(config);
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "psch=v2" + (pk.isEmpty() ? "" : "&pkey=" + pk);
    }

    /**
     * 把 MOUFLON 占位播放列表重写为可直接解析的播放列表：段名 hash XOR 反解 + 占位段行替换。
     * 非 MOUFLON 文本原样返回。
     *
     * XOR 口令以设置页的 {@code xorKeyV2} 为准（UI 可改），为空才退回 extra.json。
     */
    public static String rewriteMediaPlaylistBody(String body) {
        if (body == null) return null;
        if (!body.contains("#EXT-X-MOUFLON:")) return body;
        // pass null on purpose: xorKey() resolves the singleton itself so that a not-yet-initialised
        // Config falls back to extra.json instead of throwing while evaluating the argument
        return Mouflon.normalize(body, xorKey(null));
    }

    /** XOR 口令：设置页 {@code xorKeyV2} 优先，其次 extra.json；单测无 Config 时退回 extra.json。 */
    public static String xorKey(Config config) {
        String k = null;
        try {
            Config c = config != null ? config : Config.getInstance();
            k = c.getSettings().xorKeyV2;
        } catch (IllegalStateException e) {
            // Config not initialised yet (unit tests, very early startup): extra.json still works
            k = null;
        }
        if (k == null || k.isBlank()) k = MouflonSettings.getInstance().getXorKey();
        return k == null ? "" : k.trim();
    }

    /**
     * 播放列表里的混淆段名有几个真被当前 pkey/xorKey 配对解开了。
     * 返回 0 而段名确实存在，说明这一路的 pkey 是诱饵（播放列表照样 200），
     * 上层据此立刻报错停止，而不是让每段静静 404。
     */
    public static int decodableSegmentCount(String body) {
        return Mouflon.decodedV2Count(body, xorKey(null));
    }

    /** true when the body carries obfuscated segment names that could not be decoded. */
    public static boolean undecodedMouflonSegments(String body) {
        return Mouflon.hasV2Segments(body) && decodableSegmentCount(body) == 0;
    }

    /** 需要向段/子资源传播的鉴权查询参数白名单（缺则 403/404）。 */
    private static final String[] AUTH_PARAMS = {"psch=", "pkey=", "aclAuth=", "token=", "session="};

    /**
     * 段 / init 等子资源继承播放列表的鉴权查询参数（psch/pkey/aclAuth/token/session）。
     * Stripchat 靠 psch/pkey、Chaturbate edge 靠 session/token，段不带这些一律 403。
     */
    public static String inheritQuery(String playlistUrl, String segUrl) {
        try {
            if (playlistUrl == null || segUrl == null) return segUrl;
            URI p = URI.create(playlistUrl);
            String q = p.getRawQuery();
            if (q == null) return segUrl;
            StringBuilder keep = new StringBuilder();
            for (String kv : q.split("&")) {
                if (!isAuth(kv)) continue;
                if (already(segUrl, kv)) continue;
                if (keep.length() > 0) keep.append('&');
                keep.append(kv);
            }
            if (keep.length() == 0) return segUrl;
            return segUrl + (segUrl.contains("?") ? "&" : "?") + keep;
        } catch (Exception e) {
            return segUrl;
        }
    }

    private static boolean isAuth(String kv) {
        for (String name : AUTH_PARAMS) {
            if (kv.startsWith(name)) return true;
        }
        return false;
    }

    private static boolean already(String segUrl, String kv) {
        String name = kv.substring(0, kv.indexOf('=') + 1);
        return segUrl.contains("?" + name) || segUrl.contains("&" + name);
    }

    /**
     * pkey 查询串要发的 keyId（{@link MouflonSettings#getPkey()}），<b>不是</b> XOR 口令本身。
     * 之前这里发的是口令值，服务器把它当成一个诱饵 seed，回的是另一把密钥混淆的段名，
     * 于是播放列表看着正常（{@code #EXTINF} 有、不是广告列表）却一段都解不开、全 404。
     */
    public static String pkey(Config config) {
        String pk = MouflonSettings.getInstance().getPkey();
        if (pk == null || pk.isBlank()) {
            try {
                Config c = config != null ? config : Config.getInstance();
                pk = c.getSettings().pkeyV2;
            } catch (IllegalStateException e) {
                pk = null;
            }
        }
        return pk == null ? "" : pk.trim();
    }

    /** 调试辅助：MOUFLON 段数量统计。 */
    public static int countMouflonSegments(String body) {
        if (body == null) return 0;
        int n = 0, i = 0;
        while ((i = body.indexOf("#EXT-X-MOUFLON:URI:", i + 1)) > 0) n++;
        return n;
    }
}
