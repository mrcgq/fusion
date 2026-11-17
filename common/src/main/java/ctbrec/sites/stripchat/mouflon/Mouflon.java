package ctbrec.sites.stripchat.mouflon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Stripchat MOUFLON 还原（现网 PSCH v2 实测算法）。
 *
 * 现象：媒体播放列表里
 *   - 段 URI 行被替换成占位符 https://…/media.mp4（直接抓必 404）
 *   - 真实段 URI 挂在 #EXT-X-MOUFLON:URI: 注释行上，但其中 21~23 位的 base64 段名 hash 是
 *     XOR 混淆过的（含 '/' '+'，不是合法对象名，直接用也 404）。
 *
 * 反解（已在 edge-hls/media-hls.doppiocdn.net 现网逐段验证，还原后 HTTP 200 + sidx/moof 真字节）：
 *   key16  = sha256(xorKey)[0..15]                          // xorKey 见 {@link MouflonSettings}
 *   rev    = reverse(hash); rev 补齐到 4 的倍数（'='）
 *   data   = base64Decode(rev)                              // 22 位 -> 16 字节
 *   plain[i] = data[i] XOR key16[i]   (i < 16)             // 结果恒为 [A-Za-z0-9]{16}
 *   realUrl  = mouflonUrl.replace(hash, plain)
 *
 * 若播放列表是 V1 形态（#EXT-X-MOUFLON:FILE:<base64>），用 sha256(v1Key) 全长按字节循环异或，
 * 解出真实文件名替换掉 media.mp4 占位。
 */
public final class Mouflon {

    private static final Logger LOG = LoggerFactory.getLogger(Mouflon.class);

    /** MOUFLON v2 段名里的混淆 hash：<seq>_<21..23位 base64url 兼容字符>_<epoch>[_partN].mp4 */
    private static final Pattern V2_HASH =
            Pattern.compile("_(\\d+)_([A-Za-z0-9+/]{21,23})_(\\d+)(?:_part\\d+)?\\.[A-Za-z0-9]+(?:\\?.*)?$");

    /** 从 master / media body 里取 #EXT-X-MOUFLON:PSCH:v2:<seed> 的 seed 列表（master 里通常 ~11 个）。 */
    private static final Pattern PSCH_SEED = Pattern.compile("#EXT-X-MOUFLON:PSCH:v2:(\\S+)");

    /** 混淆段名所在的注释行 #EXT-X-MOUFLON:URI:<url>。 */
    private static final Pattern MOUFLON_URI = Pattern.compile("#EXT-X-MOUFLON:URI:(\\S+)");

    /** room slug -> 该房 master 里出现过的全部 PSCH seed（媒体播放列表反解的候选 key 来源）。 */
    private static final Map<String, List<String>> MASTER_SEEDS = new ConcurrentHashMap<>();

    private Mouflon() { }

    /** 播放列表 URL / master body 需要携带的 pkey（现网任一 PSCH seed 作 pkey 都能出真·live 列表）。 */
    public static java.util.List<String> pschSeeds(String masterBody) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (masterBody == null) return out;
        Matcher m = PSCH_SEED.matcher(masterBody);
        while (m.find()) {
            String s = m.group(1).trim();
            if (!s.isEmpty() && !out.contains(s)) out.add(s);
        }
        return out;
    }

    /**
     * 把 MOUFLON 播放列表文本还原成"EXTINF + 真实段 URI"的标准可解析文本。
     * 非 MOUFLON 文本原样返回。
     *
     * @param body   原始 m3u8 文本
     * @param xorKey V2 XOR 口令（{@link MouflonSettings#getXorKey()}）；仅作候选之一
     */
    public static String normalize(String body, String xorKey) {
        if (body == null) return null;
        boolean hasV2 = body.contains("#EXT-X-MOUFLON:URI:");
        boolean hasV1 = body.contains("#EXT-X-MOUFLON:FILE:");
        if (!hasV2 && !hasV1) return body;

        String[] lines = body.split("\r?\n", -1);

        // 候选 key：静态口令 + 本 body/master 里出现过的所有 PSCH seed（现网按 seed 轮转，
        // 逐个试、用"解出 [A-Za-z0-9]{16}"自校验，等价于真实播放器的行为）。
        List<byte[]> keyCandidates = keyCandidates(body, xorKey);
        if (keyCandidates.isEmpty()) keyCandidates = List.of(deriveKey16(xorKey));

        // ---- V2：把 MOUFLON:URI 行的 hash 反解，替换掉紧随其后的占位段行 ----
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("#EXT-X-MOUFLON:URI:")) {
                String encUrl = t.substring("#EXT-X-MOUFLON:URI:".length()).trim();
                String realUrl = decryptV2Url(encUrl, keyCandidates);
                replacePlaceholder(lines, i + 1, realUrl);
            }
        }

        // ---- V1：MOUFLON:FILE 行解密出文件名，替换 media.mp4 占位 ----
        if (hasV1) {
            List<String> v1Keys = MouflonSettings.getInstance().getV1Keys();
            for (int i = 0; i < lines.length; i++) {
                String t = lines[i].trim();
                if (t.startsWith("#EXT-X-MOUFLON:FILE:")) {
                    String b64 = t.substring("#EXT-X-MOUFLON:FILE:".length()).trim();
                    String name = decryptV1(b64, v1Keys);
                    if (name != null) replacePlaceholderFile(lines, i + 1, name);
                }
            }
        }

        return String.join("\n", lines);
    }

    /** 对单条 MOUFLON v2 段 URL 反解 hash（单一 key），返回真实可下载 URL；解不出返回原值。 */
    public static String decryptV2Url(String mouflonUrl, byte[] key16) {
        return decryptV2Url(mouflonUrl, List.of(key16));
    }

    /**
     * 对单条 MOUFLON v2 段 URL 反解 hash，在候选 key 里找第一个能把 hash 解成
     * {@code [A-Za-z0-9]{16}} 的（现网 XOR key 随 PSCH seed 轮转，必须多候选试解）。
     * 全解不出返回原值（上层拿到 404，但不会误删占位行）。
     */
    public static String decryptV2Url(String mouflonUrl, List<byte[]> keyCandidates) {
        if (mouflonUrl == null) return null;
        int q = mouflonUrl.indexOf('?');
        String path = q >= 0 ? mouflonUrl.substring(0, q) : mouflonUrl;
        String query = q >= 0 ? mouflonUrl.substring(q) : "";
        Matcher m = V2_HASH.matcher(path);
        if (!m.find()) {
            LOG.trace("MOUFLON V2: hash pattern not found in {}", path);
            return mouflonUrl;
        }
        String encHash = m.group(2);
        String plain = decryptV2HashMulti(encHash, keyCandidates);
        if (plain == null) {
            LOG.warn("MOUFLON V2: none of {} key candidates decrypted hash '{}'",
                    keyCandidates.size(), encHash);
            return mouflonUrl;
        }
        String realPath = path.substring(0, m.start(2)) + plain + path.substring(m.end(2));
        return realPath + query;
    }

    /** 遍历候选 key，返回第一个解出 [A-Za-z0-9]{16} 的明文。 */
    public static String decryptV2HashMulti(String encHash, List<byte[]> keyCandidates) {
        for (byte[] k : keyCandidates) {
            String plain = decryptV2Hash(encHash, k);
            if (plain != null) return plain;
        }
        return null;
    }

    /**
     * 组装候选 XOR key：
     *   1. 本 body 里出现的所有 PSCH seed；
     *   2. 该房 master 缓存下来的所有 seed（{@link #rememberMasterSeeds}）——媒体播放列表常只带 1 个 seed；
     *   3. 静态口令 {@code xorKey}。
     * 每个 seed 派生 sha256[:16] 与 md5 两种（现网命中 sha256[:16]，md5 兜底）。
     */
    public static List<byte[]> keyCandidates(String body, String xorKey) {
        java.util.LinkedHashSet<String> seeds = new java.util.LinkedHashSet<>();
        if (body != null) seeds.addAll(pschSeeds(body));
        seeds.addAll(allRememberedSeeds());
        if (xorKey != null && !xorKey.isBlank()) seeds.add(xorKey.trim());

        List<byte[]> keys = new ArrayList<>();
        java.util.Set<String> seenHex = new java.util.HashSet<>();
        for (String s : seeds) {
            for (byte[] k : deriveAll(s)) {
                String hex = java.util.HexFormat.of().formatHex(k);
                if (seenHex.add(hex)) keys.add(k);
            }
        }
        return keys;
    }

    /** 一个 seed 派生 sha256[:16] 与 md5（及 32-hex 直用）三种 key。 */
    private static List<byte[]> deriveAll(String seed) {
        byte[] b = seed.getBytes(StandardCharsets.UTF_8);
        List<byte[]> out = new ArrayList<>();
        out.add(Arrays.copyOf(sha256(b), 16));
        out.add(md5(b));
        if (seed.length() == 32 && seed.matches("[0-9a-fA-F]{32}")) {
            byte[] direct = new byte[16];
            for (int i = 0; i < 16; i++) direct[i] = (byte) Integer.parseInt(seed.substring(i * 2, i * 2 + 2), 16);
            out.add(direct);
        }
        return out;
    }

    /** 记住某房 master 里的全部 PSCH seed，媒体播放列表反解时一并作候选。 */
    public static void rememberMasterSeeds(String roomSlug, String masterBody) {
        if (roomSlug == null || masterBody == null) return;
        List<String> seeds = pschSeeds(masterBody);
        if (!seeds.isEmpty()) MASTER_SEEDS.put(roomSlug.toLowerCase(), seeds);
    }

    public static void forgetMasterSeeds(String roomSlug) {
        if (roomSlug != null) MASTER_SEEDS.remove(roomSlug.toLowerCase());
    }

    private static List<String> allRememberedSeeds() {
        List<String> all = new ArrayList<>();
        for (List<String> v : MASTER_SEEDS.values()) all.addAll(v);
        return all;
    }

    /** true when the body carries obfuscated v2 segment names that have to be decoded first. */
    public static boolean hasV2Segments(String body) {
        return body != null && body.contains("#EXT-X-MOUFLON:URI:");
    }

    /**
     * How many of the obfuscated v2 segment names actually decode to a real {@code
     * [A-Za-z0-9]{16}} name with the current key pool.
     *
     * This is the cheap way to tell whether the playlist was requested with the right {@code pkey}:
     * the server picks the obfuscation key from the pkey, so a playlist fetched with a decoy pkey
     * still looks perfectly valid ({@code #EXTINF} present, no advert marker) but none of its hashes
     * decode. A correct pkey decodes every line.
     */
    public static int decodedV2Count(String body, String xorKey) {
        if (!hasV2Segments(body)) return 0;
        List<byte[]> candidates = keyCandidates(body, xorKey);
        if (candidates.isEmpty()) candidates = List.of(deriveKey16(xorKey));
        int ok = 0;
        Matcher line = MOUFLON_URI.matcher(body);
        while (line.find()) {
            Matcher hash = V2_HASH.matcher(line.group(1));
            if (hash.find() && decryptV2HashMulti(hash.group(2), candidates) != null) {
                ok++;
            }
        }
        return ok;
    }

    /**
     * 22 位 base64 hash -> 16 位明文 hash（[A-Za-z0-9]{16}）。
     * 算法：reverse + base64 解码 + 逐字节 XOR(sha256(xorKey)[:16])。
     */
    public static String decryptV2Hash(String encHash, byte[] key16) {
        try {
            String rev = new StringBuilder(encHash).reverse().toString();
            StringBuilder pad = new StringBuilder(rev);
            while (pad.length() % 4 != 0) pad.append('=');
            byte[] data = Base64.getDecoder().decode(pad.toString());
            StringBuilder out = new StringBuilder();
            for (int i = 0; i < data.length && i < key16.length; i++) {
                out.append((char) ((data[i] & 0xFF) ^ (key16[i] & 0xFF)));
            }
            String plain = out.toString();
            if (plain.length() == 16 && plain.matches("[A-Za-z0-9]+")) {
                return plain;
            }
            LOG.trace("MOUFLON V2: decrypted '{}' -> '{}' rejected (not [A-Za-z0-9]{16})", encHash, plain);
            return null;
        } catch (Exception e) {
            LOG.trace("MOUFLON V2 decrypt error for {}: {}", encHash, e.getMessage());
            return null;
        }
    }

    /** V1：base64 -> 与 sha256(v1Key) 逐字节循环异或 -> 文件名。 */
    public static String decryptV1(String b64, List<String> keys) {
        byte[] data;
        try {
            data = Base64.getDecoder().decode(b64);
        } catch (Exception e) {
            return null;
        }
        for (String key : keys) {
            if (key == null || key.isEmpty()) continue;
            byte[] kh = sha256(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < data.length; i++) {
                sb.append((char) ((data[i] & 0xFF) ^ (kh[i % kh.length] & 0xFF)));
            }
            String name = sb.toString();
            if (name.matches("[A-Za-z0-9._-]+")) return name;
        }
        return null;
    }

    /** xorKey 若已是 32-hex 直接用；否则 sha256(xorKey)[:16] 转 32-hex（现网即 tOcYOap4Ty1l9Jzb）。 */
    public static byte[] deriveKey16(String xorKey) {
        String k = xorKey == null ? "" : xorKey.trim();
        if (k.length() == 32 && k.matches("[0-9a-fA-F]{32}")) {
            byte[] b = new byte[16];
            for (int i = 0; i < 16; i++) b[i] = (byte) Integer.parseInt(k.substring(i * 2, i * 2 + 2), 16);
            return b;
        }
        byte[] d = sha256(k.getBytes(StandardCharsets.UTF_8));
        return java.util.Arrays.copyOf(d, 16);
    }

    private static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException("no sha-256", e);
        }
    }

    private static byte[] md5(byte[] in) {
        try {
            return MessageDigest.getInstance("MD5").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException("no md5", e);
        }
    }

    private static void replacePlaceholder(String[] lines, int index, String realUrl) {
        if (index >= lines.length || realUrl == null) return;
        String next = lines[index].trim();
        if (next.startsWith("#EXT-X-PART:") && next.contains("media.mp4")) {
            lines[index] = next.replaceAll("URI=\"[^\"]*media\\.mp4\"", "URI=\"" + realUrl + "\"");
        } else if (next.endsWith("media.mp4") || next.equals("media.mp4")) {
            lines[index] = realUrl;
        }
    }

    private static void replacePlaceholderFile(String[] lines, int index, String filename) {
        if (index >= lines.length) return;
        String next = lines[index].trim();
        if (next.endsWith("media.mp4")) {
            int slash = next.lastIndexOf('/');
            lines[index] = slash >= 0 ? next.substring(0, slash + 1) + filename : filename;
        }
    }
}
