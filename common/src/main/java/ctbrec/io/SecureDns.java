package ctbrec.io;

import okhttp3.Dns;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抗污染 / 抗抖动的解析器（okhttp {@link Dns}），给所有站点用。
 *
 * 现场实测到的两类故障（都会让录制"连不上"而不是"录不好"）：
 *   1) 系统 resolver 被污染：chaturbate.com / stripchat.com 解析到连不通的假 IP
 *      （如 108.160.166.57 / 174.37.175.229，TCP 443 全部超时），而真正的 CDN
 *      edge-hls/media-hls.doppiocdn.net 解析正常且可达。
 *   2) 解析结果随出口 IP/时间漂移（IP 一变，旧解析立刻失效）。
 *
 * 策略（正常网络下零副作用，只在坏结果出现时才介入）：
 *   A. 先系统解析；把结果拿去做 TCP 443 可达性探测，有可达的就用它。
 *   B. 全不可达 -&gt; 用外部 UDP DNS（53 端口，非 53/UDP 被封的也能退回 TCP 53）重新解析，
 *      再探测可达性；这一步专门用来绕开被污染的本地 resolver。
 *   C. 还不行 -&gt; 用内置的已验证 CDN 兜底 IP（仅录制取流域名）。
 *   D. 全都失败 -&gt; 原样返回系统解析结果，让上层报常规错误。
 *
 * 结果带 TTL 缓存；{@link #clearCaches()} 由 {@link EgressGuard} 在出口 IP 变化时调用。
 */
public final class SecureDns implements Dns {

    private static final Logger LOG = LoggerFactory.getLogger(SecureDns.class);

    /** 外部 DNS（UDP 53 优先，其次 TCP 53）。 */
    private static final String[] EXTERNAL_DNS = {"1.1.1.1", "223.5.5.5", "9.9.9.9", "119.29.29.29", "8.8.8.8"};

    /**
     * 已实测可达的取流 CDN 兜底 IP（现场验证过 TCP 443 可达）。
     * 只是最后一道保险；正常情况永远走 A/B。
     */
    private static final Map<String, String[]> STATIC_FALLBACK = new LinkedHashMap<>();
    static {
        STATIC_FALLBACK.put("edge-hls.doppiocdn.net", new String[]{"18.67.51.111", "18.67.51.15", "18.67.51.50", "18.67.51.68"});
        STATIC_FALLBACK.put("media-hls.doppiocdn.net", new String[]{"3.168.167.17", "3.168.167.49", "3.168.167.71", "3.168.167.86"});
    }

    private static final SecureRandom RNG = new SecureRandom();
    private static final SecureDns INSTANCE = new SecureDns();

    private static final long CACHE_TTL_MS = 5 * 60_000L;
    private static final int PROBE_TIMEOUT_MS = 900;
    private static final int PROBE_BUDGET_MS = 2_600;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    private static final class Entry {
        final List<InetAddress> addresses;
        final long at;
        Entry(List<InetAddress> addresses) { this.addresses = addresses; this.at = System.currentTimeMillis(); }
        boolean fresh() { return System.currentTimeMillis() - at < CACHE_TTL_MS; }
    }

    public static SecureDns get() {
        return INSTANCE;
    }

    /** 出口 IP 变化 / 网络切换时清缓存，强制重新解析。 */
    public void clearCaches() {
        cache.clear();
        LOG.debug("DNS caches cleared (egress change)");
    }

    @Override
    public List<InetAddress> lookup(String hostname) throws UnknownHostException {
        Entry cached = cache.get(hostname);
        if (cached != null && cached.fresh()) return cached.addresses;

        List<InetAddress> resolved = resolve(hostname);
        cache.put(hostname, new Entry(resolved));
        return resolved;
    }

    /**
     * 走 HTTP/SOCKS 代理时**不能**做直连可达性探测：本机对站点域名本来就没有直连路由，
     * 探测必然全灭，会把正确解析结果丢掉还白丢几秒。有代理时直接交系统解析：
     *   - HTTP 代理：okhttp 根本不调 Dns，域名由代理解析；
     *   - SOCKS5 代理：okhttp 把域名交给代理端解析，本地解析不参与建连。
     * 仅 DIRECT 模式启用污染纠偏。
     */
    private List<InetAddress> resolve(String hostname) throws UnknownHostException {
        if (proxyActive()) {
            return safeSystemLookupOrThrow(hostname);
        }

        long budgetEnd = System.currentTimeMillis() + PROBE_BUDGET_MS;

        // A. 系统解析 + 可达性验证
        List<InetAddress> sys = safeSystemLookup(hostname);
        if (!sys.isEmpty()) {
            List<InetAddress> reachable = probeReachable(sys, budgetEnd);
            if (!reachable.isEmpty()) {
                if (reachable.size() != sys.size()) {
                    LOG.debug("DNS {}: system answer partially reachable, kept {}/{}", hostname, reachable.size(), sys.size());
                }
                return reachable;
            }
            LOG.warn("DNS {}: system answer {} is unreachable (polluted/blackholed?) - trying external DNS",
                    hostname, ips(sys));
        } else {
            LOG.warn("DNS {}: system lookup failed - trying external DNS", hostname);
        }

        // B. 外部 DNS 纠偏
        List<InetAddress> ext = externalLookup(hostname);
        if (!ext.isEmpty()) {
            List<InetAddress> reachable = probeReachable(ext, budgetEnd);
            if (!reachable.isEmpty()) {
                LOG.info("DNS {} recovered via external DNS -> {}", hostname, ips(reachable));
                return reachable;
            }
            LOG.warn("DNS {}: external answers {} also unreachable", hostname, ips(ext));
        }

        // C. 静态兜底（只覆盖取流 CDN）
        String[] fallback = STATIC_FALLBACK.get(hostname.toLowerCase());
        if (fallback != null) {
            List<InetAddress> fb = byIpLiteral(fallback);
            if (!fb.isEmpty()) {
                LOG.info("DNS {} using static CDN fallback -> {}", hostname, ips(fb));
                return fb;
            }
        }

        // D. 交回系统结果，让上层正常报错
        if (!sys.isEmpty()) return sys;
        if (!ext.isEmpty()) return ext;
        throw new UnknownHostException(hostname);
    }

    /** 有 HTTP/SOCKS 代理配置时返回 true（此时不该做直连可达性探测）。 */
    private static boolean proxyActive() {
        return System.getProperty("http.proxyHost") != null
                || System.getProperty("https.proxyHost") != null
                || System.getProperty("socksProxyHost") != null;
    }

    /** 代理模式：拿系统解析结果；失败则外部 DNS；再失败交给 JDK 抛标准异常。 */
    private List<InetAddress> safeSystemLookupOrThrow(String hostname) throws UnknownHostException {
        List<InetAddress> out = safeSystemLookup(hostname);
        if (out.isEmpty()) {
            out = externalLookup(hostname);
        }
        if (out.isEmpty()) {
            return List.of(InetAddress.getAllByName(hostname));
        }
        return out;
    }

    private List<InetAddress> safeSystemLookup(String hostname) {
        try {
            InetAddress[] all = InetAddress.getAllByName(hostname);
            List<InetAddress> out = new ArrayList<>();
            // IPv4 优先：这里的目标服务基本都是 A 记录可达，v6 常黑洞
            for (InetAddress a : all) {
                if (a.getAddress().length == 4) out.add(a);
            }
            for (InetAddress a : all) {
                if (a.getAddress().length != 4) out.add(a);
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 并发不了就用顺序：对每个候选做 TCP 443 握手，保留可达的。 */
    private List<InetAddress> probeReachable(List<InetAddress> candidates, long budgetEnd) {
        List<InetAddress> ok = new ArrayList<>();
        for (InetAddress a : candidates) {
            if (System.currentTimeMillis() > budgetEnd) break;
            if (tcpConnectable(a, 443, PROBE_TIMEOUT_MS)) ok.add(a);
        }
        return ok;
    }

    private boolean tcpConnectable(InetAddress addr, int port, int timeoutMs) {
        try (Socket s = new Socket()) {
            s.connect(new InetSocketAddress(addr, port), timeoutMs);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private List<InetAddress> byIpLiteral(String[] ips) {
        List<InetAddress> out = new ArrayList<>();
        for (String ip : ips) {
            try {
                byte[] b = new byte[4];
                String[] p = ip.split("\\.");
                if (p.length != 4) continue;
                for (int i = 0; i < 4; i++) b[i] = (byte) Integer.parseInt(p[i]);
                out.add(InetAddress.getByAddress(ip, b));
            } catch (Exception ignored) { }
        }
        return out;
    }

    // ------------------------------------------------------------------
    //  极简 DNS 客户端（A 记录，UDP 53，失败退回 TCP 53），无第三方依赖
    // ------------------------------------------------------------------

    private List<InetAddress> externalLookup(String hostname) {
        for (String server : EXTERNAL_DNS) {
            List<String> ips = dnsARecords(server, hostname, true);
            if (ips.isEmpty()) ips = dnsARecords(server, hostname, false);
            if (!ips.isEmpty()) {
                List<InetAddress> addrs = byIpLiteral(ips.toArray(new String[0]));
                if (!addrs.isEmpty()) {
                    LOG.debug("DNS {} answered by {} -> {}", hostname, server, ips);
                    return addrs;
                }
            }
        }
        return List.of();
    }

    private List<String> dnsARecords(String server, String hostname, boolean udp) {
        byte[] query = buildQuery(hostname);
        try {
            byte[] resp;
            if (udp) {
                try (DatagramSocket sock = new DatagramSocket()) {
                    sock.setSoTimeout(1500);
                    DatagramPacket out = new DatagramPacket(query, query.length,
                            InetAddress.getByName(server), 53);
                    sock.send(out);
                    byte[] buf = new byte[4096];
                    DatagramPacket in = new DatagramPacket(buf, buf.length);
                    sock.receive(in);
                    resp = new byte[in.getLength()];
                    System.arraycopy(buf, 0, resp, 0, in.getLength());
                }
            } else {
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(server, 53), 1500);
                    s.setSoTimeout(1500);
                    ByteBuffer len = ByteBuffer.allocate(2);
                    len.putShort((short) query.length);
                    s.getOutputStream().write(len.array());
                    s.getOutputStream().write(query);
                    s.getOutputStream().flush();
                    byte[] lenBuf = new byte[2];
                    readFully(s, lenBuf, 2);
                    int n = ByteBuffer.wrap(lenBuf).getShort() & 0xFFFF;
                    if (n <= 0 || n > 8192) return List.of();
                    byte[] body = new byte[n];
                    readFully(s, body, n);
                    resp = body;
                }
            }
            return parseARecords(resp, hostname);
        } catch (Exception e) {
            LOG.trace("DNS query {} via {}({}) failed: {}", hostname, server, udp ? "udp" : "tcp", e.getMessage());
            return List.of();
        }
    }

    private void readFully(Socket s, byte[] buf, int n) throws IOException {
        int off = 0;
        while (off < n) {
            int r = s.getInputStream().read(buf, off, n - off);
            if (r < 0) throw new IOException("eof");
            off += r;
        }
    }

    private byte[] buildQuery(String hostname) {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        int id = RNG.nextInt(0xFFFF);
        // header: id, flags(RD), qdcount=1, ancount=0, nscount=0, arcount=0
        b.write((id >> 8) & 0xFF); b.write(id & 0xFF);
        b.write(0x01); b.write(0x00);
        b.write(0x00); b.write(0x01);
        b.write(0); b.write(0); b.write(0); b.write(0); b.write(0); b.write(0);
        for (String label : hostname.split("\\.")) {
            if (label.isEmpty()) continue;
            byte[] lb = label.getBytes();
            b.write(lb.length);
            b.write(lb, 0, lb.length);
        }
        b.write(0);
        b.write(0); b.write(1);   // QTYPE = A
        b.write(0); b.write(1);   // QCLASS = IN
        return b.toByteArray();
    }

    /** 解析响应里的 A 记录（正确处理名字压缩指针）。 */
    private List<String> parseARecords(byte[] data, String hostname) {
        List<String> out = new ArrayList<>();
        if (data.length < 12) return out;
        int qdcount = u16(data, 4);
        int ancount = u16(data, 6);
        int i = 12;
        for (int q = 0; q < qdcount; q++) {
            i = skipName(data, i);
            i += 4; // qtype + qclass
            if (i > data.length) return out;
        }
        for (int a = 0; a < ancount; a++) {
            i = skipName(data, i);
            if (i + 10 > data.length) return out;
            int type = u16(data, i);
            int rdlen = u16(data, i + 8);
            i += 10;
            if (i + rdlen > data.length) return out;
            if (type == 1 && rdlen == 4) {
                out.add((data[i] & 255) + "." + (data[i + 1] & 255) + "."
                        + (data[i + 2] & 255) + "." + (data[i + 3] & 255));
            }
            i += rdlen;
        }
        return out;
    }

    /** 跳过 DNS 名字（含 0xC0 压缩指针），返回下一个字段起点。 */
    private int skipName(byte[] data, int i) {
        int guard = 0;
        while (i < data.length && guard++ < 128) {
            int len = data[i] & 0xFF;
            if (len == 0) return i + 1;
            if ((len & 0xC0) == 0xC0) return i + 2;   // 指针：2 字节结束本名
            i += 1 + len;
        }
        return data.length;
    }

    private int u16(byte[] d, int off) {
        return ((d[off] & 0xFF) << 8) | (d[off + 1] & 0xFF);
    }

    private static String ips(List<InetAddress> list) {
        StringBuilder sb = new StringBuilder("[");
        for (InetAddress a : list) {
            if (sb.length() > 1) sb.append(", ");
            sb.append(a.getHostAddress());
        }
        return sb.append(']').toString();
    }
}
