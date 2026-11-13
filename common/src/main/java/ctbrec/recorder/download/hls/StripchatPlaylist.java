package ctbrec.recorder.download.hls;

import ctbrec.Config;
import ctbrec.Model;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * MOUFLON PSCH v2 播放列表重写（Stripchat 专用，其它站点原样返回）。
 *
 * 现象（现网实测）：媒体播放列表的段 URI 行被替换成占位符（如 https://…/media.mp4，必 404），
 * 真实段 URI 挂在 #EXT-X-MOUFLON:URI: 注释行上（MOUFLON 行数量 = 段数量 + 1，多出的首行
 * 指向 map/init 之后的首个真实段；段按 part0/part1/… 排列）。
 *
 * 这里在解析前把文本重写成"EXTINF 行 + 真实段 URI"的标准形式，并把播放列表 URL 的
 * psch/pkey 查询参数补回真实段 URL —— 三者缺一必 404。
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

    /**
     * 媒体播放列表 URL 规范化：补齐 psch=v2&pkey（若缺）。
     */
    public static String rewritePlaylistUrl(String url, Config config, Model model) {
        if (!isStripchatModel(model) || url == null || url.isEmpty()) return url;
        String lower = url.toLowerCase();
        if (lower.contains("psch=") || !lower.contains("doppiocdn")) return url;
        String pk = ctbrec.sites.stripchat.StripchatEdgeResolver.pkey(config);
        String sep = url.contains("?") ? "&" : "?";
        return url + sep + "psch=v2" + (pk.isEmpty() ? "" : "&pkey=" + pk);
    }

    /**
     * 把 MOUFLON 占位播放列表重写为可直接解析的播放列表。
     * 输入：原始 m3u8 文本；输出：EXTINF 与真实段行一一对应的文本。
     */
    public static String rewriteMediaPlaylistBody(String body) {
        if (body == null || !body.contains("#EXT-X-MOUFLON:URI:")) return body;
        // MOUFLON URI 列表（按出现顺序 = 段顺序）
        List<String> real = new ArrayList<>();
        List<String> initUri = new ArrayList<>();
        for (String line : body.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("#EXT-X-MOUFLON:URI:")) {
                real.add(t.substring("#EXT-X-MOUFLON:URI:".length()).trim());
            } else if (t.startsWith("#EXT-X-MAP:URI=\"")) {
                initUri.add(t.substring("#EXT-X-MAP:URI=\"".length(), t.lastIndexOf('"')).trim());
            }
        }
        if (real.isEmpty()) return body;

        // 段时长序列（EXTINF 出现顺序；LL-HLS 无 EXTINF 时退化为 part-inf / 2s）
        List<String> extinf = new ArrayList<>();
        for (String line : body.split("\r?\n")) {
            String t = line.trim();
            if (t.startsWith("#EXTINF:")) extinf.add(t);
        }

        StringBuilder out = new StringBuilder();
        String[] lines = body.split("\r?\n");
        boolean mapWritten = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#EXT-X-MOUFLON:URI:")) continue;  // 占位与 MOUFLON 行稍后重放
            if (t.startsWith("#EXT-X-MAP:")) {
                out.append(line).append('\n');
                mapWritten = true;
                continue;
            }
            if (t.startsWith("#EXT-X-PART:") || t.startsWith("#EXT-X-PART-INF") || t.startsWith("#EXT-X-SERVER-CONTROL")) {
                continue; // 普通录制不需要 part 级行
            }
            out.append(line).append('\n');
            if (t.startsWith("#EXTINF:")) {
                if (!real.isEmpty()) {
                    out.append(real.remove(0)).append('\n');
                }
            }
        }
        // 没有任何 EXTINF（纯 LL-HLS part playlist）：为每个真实段合成 EXTINF
        if (extinf.isEmpty() && !real.isEmpty()) {
            StringBuilder all = new StringBuilder();
            for (String line : lines) {
                String t = line.trim();
                if (t.isEmpty() || t.startsWith("#EXT-X-MOUFLON:URI:") || t.startsWith("#EXT-X-PART")) continue;
                all.append(line).append('\n');
            }
            StringBuilder segs = new StringBuilder();
            for (String u : real) {
                segs.append("#EXTINF:2.000,\n").append(u).append('\n');
            }
            // 在 media sequence 行之后插入段
            String head = all.toString();
            int idx = head.indexOf("#EXT-X-MAP");
            if (idx >= 0) {
                int nl = head.indexOf('\n', idx);
                return head.substring(0, nl + 1) + segs + head.substring(nl + 1);
            }
            return head + segs;
        }
        if (!mapWritten && !initUri.isEmpty()) {
            out.insert(0, "#EXT-X-MAP:URI=\"" + initUri.get(0) + "\"\n");
        }
        return out.toString();
    }

    /**
     * 真实段 URI 继承播放列表的 psch/pkey 查询参数（缺了就 404）。
     */
    public static String inheritQuery(String playlistUrl, String segUrl) {
        try {
            if (playlistUrl == null || !playlistUrl.contains("psch=") || segUrl.contains("psch=")) return segUrl;
            URI p = URI.create(playlistUrl);
            String q = p.getRawQuery();
            if (q == null) return segUrl;
            StringBuilder keep = new StringBuilder();
            for (String kv : q.split("&")) {
                if (kv.startsWith("psch=") || kv.startsWith("pkey=") || kv.startsWith("aclAuth=")) {
                    if (keep.length() > 0) keep.append('&');
                    keep.append(kv);
                }
            }
            if (keep.length() == 0) return segUrl;
            return segUrl + (segUrl.contains("?") ? "&" : "?") + keep;
        } catch (Exception e) {
            return segUrl;
        }
    }

    /** 调试辅助：MOUFLON 段数量统计。 */
    public static int countMouflonSegments(String body) {
        if (body == null) return 0;
        int n = 0, i = 0;
        while ((i = body.indexOf("#EXT-X-MOUFLON:URI:", i + 1)) > 0) n++;
        return n;
    }

    public static List<String> unused() {
        return Collections.emptyList();
    }
}
