package ctbrec.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 出口 IP 变动守卫（IP-flapping immunity）。
 *
 * 为什么要它：Chaturbate 的 edge/master URL 里那段 {@code ?token=}(RSA-OAEP) 与 {@code ?session=}
 * 是和"当前看到的出口 IP + 会话"绑的；Stripchat 的 CF {@code __cf_bm}/{@code _cfuvid} 与 CSRF/JWT
 * 也随出口 IP 漂移。只要出口 IP 变了（拨号重连 / Clash 换节点 / 4G 掉线 / 双栈切换），缓存里的
 * token 立刻 401/403 —— 老逻辑只会"连续失败→停录"，于是 IP 一变录制就断。
 *
 * 这里用一个守护线程定期打 {@code /cdn-cgi/trace}（Chaturbate/Stripchat 都在 Cloudflare 后面，
 * 该端点直接回 {@code ip=<出口 IP>}），一旦出口 IP 变化，回调所有监听者：站点各自去重取
 * edge/token、重取 PSCH pkey、必要时重登。这样 IP 抖动只造成"下一段自动换 token 续上"，不再断流。
 *
 * 探测走 DIRECT（与录制实际直连一致）；探不到（离线）时保持上次结论、不误报变更。
 */
public final class EgressGuard {

    private static final Logger LOG = LoggerFactory.getLogger(EgressGuard.class);
    private static final Pattern IP_LINE = Pattern.compile("(?m)^ip=([0-9a-fA-F:.]+)\\s*$");

    /** 依次尝试的出口探测点（任一返回 ip= 即可）。 */
    private static final List<String> PROBES = List.of(
            "https://chaturbate.com/cdn-cgi/trace",
            "https://stripchat.com/cdn-cgi/trace",
            "https://www.cloudflare.com/cdn-cgi/trace"
    );

    private static final EgressGuard INSTANCE = new EgressGuard();

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private volatile String lastIp = null;
    private volatile long intervalMs = 60_000L;
    private volatile boolean enabled = true;
    private volatile boolean started = false;
    private Thread worker;

    private EgressGuard() { }

    public static EgressGuard get() {
        return INSTANCE;
    }

    public void configure(boolean enabled, long intervalMs) {
        this.enabled = enabled;
        this.intervalMs = Math.max(15_000L, intervalMs);
    }

    /** 注册出口 IP 变化回调（弱语义：任何一次变更后要刷新的东西都往这挂）。 */
    public void addListener(Runnable r) {
        if (r != null && !listeners.contains(r)) listeners.add(r);
    }

    public String currentIp() {
        return lastIp;
    }

    /** 立即触发一次变更处理（例如首次登录成功后主动预热）。 */
    public void fireChange() {
        notifyListeners();
    }

    public synchronized void start() {
        if (started) return;
        started = true;
        worker = new Thread(this::loop, "EgressGuard");
        worker.setDaemon(true);
        worker.setPriority(Thread.MIN_PRIORITY);
        worker.start();
    }

    private void loop() {
        // 先取一次基线（不设监听、只记录）
        String ip = probe();
        lastIp = ip;
        LOG.info("EgressGuard baseline egress IP = {}", ip);
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            if (!enabled) continue;
            String cur = probe();
            if (cur == null) {                       // 探测失败：不清基线、不误报
                LOG.debug("EgressGuard probe failed, keeping last IP {}", lastIp);
                continue;
            }
            String prev = lastIp;
            lastIp = cur;
            if (prev != null && !prev.equals(cur)) {
                LOG.warn("Egress IP changed {} -> {} — refreshing site sessions/tokens", prev, cur);
                notifyListeners();
            }
        }
    }

    private void notifyListeners() {
        for (Runnable r : listeners) {
            try {
                r.run();
            } catch (Exception e) {
                LOG.debug("Egress listener failed", e);
            }
        }
    }

    /**
     * 打探测点，返回 {@code ip=} 的值；全部失败返回 null。
     *
     * 用 {@link HttpURLConnection} 而非 java.net.http：它会走 ctbrec 在
     * {@link HttpClient#reconfigure()} 里设定的 {@code http(s).proxyHost}/{@code socksProxyHost}
     * 系统属性。很多网络（本机就是）对站点域名没有直连路由、只能经代理出网——代理出口 IP
     * 才是站点真正看到的 IP，必须经代理探测才有意义。
     */
    private String probe() {
        for (String url : PROBES) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(url).openConnection();
                conn.setConnectTimeout(8000);
                conn.setReadTimeout(8000);
                conn.setInstanceFollowRedirects(true);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                if (conn.getResponseCode() / 100 != 2) continue;
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line).append('\n');
                }
                Matcher m = IP_LINE.matcher(sb);
                if (m.find()) return m.group(1).trim();
            } catch (Exception ignored) {
                // 试下一个探测点
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        return null;
    }
}
