package ctbrec.sites.stripchat;

import ctbrec.Config;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static ctbrec.io.HttpConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Stripchat username -> 模型信息解析，绕开现网被反爬封锁的 /cam（HTTP 418）。
 *
 * 两条实测可用的通路（均 200，无需 JS 过验）：
 *   1) /api/front/v4/models/search/suggestion?query=&lt;name&gt;&amp;limit=..&amp;primaryTag=girls
 *      -> models[] 精确匹配 username 的那条，含 id / status / isLive / isVr（离线时 status 可能为 ""）。
 *   2) /api/front/models/&lt;id&gt;  -> {id,username,status,isLive,isDeleted,isApprovedModel,avatarUrl}
 *      （需要 id，用于持续在线监控；status/isLive 权威）。
 *
 * 解析顺序：命中缓存直接用 -&gt; suggestion 解析出 id -&gt; 之后状态走 /api/front/models/{id}。
 * suggestion 对已改名/下架账号可能空返回，此时保留上一次 id 供 /models/{id} 兜底。
 */
public final class StripchatIdResolver {

    private static final Logger LOG = LoggerFactory.getLogger(StripchatIdResolver.class);

    /** name(小写) -> 已知数值 id（一次解析长期复用，IP 变化不影响 id）。 */
    private static final Map<String, Long> ID_CACHE = new ConcurrentHashMap<>();

    private StripchatIdResolver() { }

    /**
     * 返回一个"最小可用"的模型信息 JSON，形状对齐 /cam：{user:{user:{id,status,isLive,isVr}}}
     * 或 {error:"Not Found"}。调用方按既有 isOnline()/getMasterPlaylistUrl() 逻辑消费。
     */
    public static JSONObject lookup(Stripchat site, String username) throws IOException {
        String key = username.toLowerCase(Locale.ROOT);
        Long known = ID_CACHE.get(key);

        // 已知 id：走权威的按 id 状态查询（不受 418 影响）
        if (known != null) {
            JSONObject byId = statusById(site, known);
            if (byId != null) return byId;
            // id 查询失败（网络）时回落到 suggestion 再解析一次
        }

        JSONObject suggested = suggestion(site, username);
        if (suggested != null) {
            long id = suggested.optLong("id", suggested.optLong("streamName", -1));
            if (id > 0) ID_CACHE.put(key, id);
            return wrapSuggestion(suggested);
        }

        // suggestion 空（可能改名/下架）：若之前有 id，用它查状态
        if (known != null) {
            JSONObject byId = statusById(site, known);
            if (byId != null) return byId;
        }

        JSONObject nf = new JSONObject();
        nf.put("error", "Not Found");
        return nf;
    }

    /** 记住一个已知 id（例如从 URL 直接给了 id 的形态）。 */
    public static void remember(String username, long id) {
        if (id > 0) ID_CACHE.put(username.toLowerCase(Locale.ROOT), id);
    }

    public static void invalidate(String username) {
        ID_CACHE.remove(username.toLowerCase(Locale.ROOT));
    }

    /** GET /api/front/v4/models/search/suggestion 精确匹配 username，返回该 model 对象。 */
    private static JSONObject suggestion(Stripchat site, String username) {
        String url = site.getBaseUrl() + "/api/front/v4/models/search/suggestion?query="
                + URLEncoder.encode(username, UTF_8) + "&limit=12&primaryTag=girls&uniq=" + site.getUniq();
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, site.getBaseUrl())
                .header(USER_AGENT, StripchatEdgeResolver.userAgent(Config.getInstance()))
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (!resp.isSuccessful()) {
                LOG.debug("suggestion {} -> HTTP {}", username, resp.code());
                return null;
            }
            JSONObject json = new JSONObject(resp.body().string());
            JSONArray models = json.optJSONArray("models");
            if (models == null) return null;
            JSONObject exact = null, prefix = null;
            for (int i = 0; i < models.length(); i++) {
                JSONObject m = models.getJSONObject(i);
                String un = m.optString("username");
                if (un.equalsIgnoreCase(username)) { exact = m; break; }
                if (prefix == null && un.toLowerCase(Locale.ROOT).startsWith(username.toLowerCase(Locale.ROOT))) {
                    prefix = m;
                }
            }
            return exact != null ? exact : prefix;
        } catch (Exception e) {
            LOG.debug("suggestion lookup failed for {}", username, e);
            return null;
        }
    }

    /** GET /api/front/models/{id} -> 权威状态。 */
    private static JSONObject statusById(Stripchat site, long id) {
        String url = site.getBaseUrl() + "/api/front/models/" + id;
        Request req = new Request.Builder()
                .url(url)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(REFERER, site.getBaseUrl())
                .header(USER_AGENT, StripchatEdgeResolver.userAgent(Config.getInstance()))
                .build();
        try (Response resp = site.getHttpClient().execute(req)) {
            if (!resp.isSuccessful()) {
                LOG.debug("statusById {} -> HTTP {}", id, resp.code());
                return null;
            }
            JSONObject m = new JSONObject(resp.body().string());
            if (!m.has("id")) return null;
            JSONObject user = new JSONObject()
                    .put("id", m.optLong("id"))
                    .put("status", m.optString("status"))
                    .put("isLive", m.optBoolean("isLive"))
                    .put("isDeleted", m.optBoolean("isDeleted"))
                    .put("isApprovedModel", m.optBoolean("isApprovedModel", true))
                    .put("statusChangedAt", m.optString("statusChangedAt", ""));
            // isVr 不在该端点，保留默认 false
            return new JSONObject().put("user", new JSONObject().put("user", user));
        } catch (Exception e) {
            LOG.debug("statusById failed for {}", id, e);
            return null;
        }
    }

    private static JSONObject wrapSuggestion(JSONObject m) {
        JSONObject user = new JSONObject()
                .put("id", m.optLong("id", m.optLong("streamName", -1)))
                .put("status", m.optString("status"))
                .put("isLive", m.optBoolean("isLive"))
                .put("isVr", m.optBoolean("isVr"))
                .put("isDeleted", m.optBoolean("isDeleted"))
                .put("isApprovedModel", m.optBoolean("isApprovedModel", true))
                .put("statusChangedAt", m.optString("statusChangedAt", ""));
        JSONObject out = new JSONObject().put("user", new JSONObject().put("user", user));
        // 供离线状态映射：suggestion 命中但 status 为空/isLive 假 -> 视为 off，避免 Unknown
        if (user.optString("status").isEmpty() && !user.optBoolean("isLive")) {
            user.put("status", "off");
        }
        return out;
    }
}
