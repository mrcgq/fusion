package ctbrec.sites.stripchat.mouflon;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import ctbrec.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * MOUFLON 口令，落在配置目录的 extra.json，可热改（{@link #reload()}）。
 *
 * 现网 V2 XOR 口令 = tOcYOap4Ty1l9Jzb（{@link Mouflon#deriveKey16} 会 sha256 它取前 16 字节，
 * 得 1838c78f543606e6de5fc81167f36e5a）。V1 口令回退列表用于老式 #EXT-X-MOUFLON:FILE。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MouflonSettings {

    private static final Logger LOG = LoggerFactory.getLogger(MouflonSettings.class);
    private static final String FILENAME = "extra.json";

    private static volatile MouflonSettings instance;

    /** 现网 PSCH v2 XOR 口令（会被 sha256[:16] 派生）。 */
    private String xorKey = "tOcYOap4Ty1l9Jzb";

    /**
     * 查询串 {@code pkey} 要发的 <b>keyId</b>，不是口令本身。
     *
     * master 里的 {@code #EXT-X-MOUFLON:PSCH:v2:<x>} 有 ~11 行，只有这一行是真 keyId，其余是诱饵：
     * 用诱饵当 pkey，媒体播放列表照样 200 且带 {@code #EXTINF}（不是 CPA 广告列表），但里面的段名 hash
     * 是另一把密钥混淆的，用 {@link #xorKey} 一个都解不开，于是每段 404。真配对是
     * {@code pkey=NTK9aqcLmNFMWrpQ <-> xorKey=tOcYOap4Ty1l9Jzb}（实测逐段 200 + sidx 真字节）。
     * 运行时若发现别的配对可用，会由 {@code StripchatPkey} 写回这里并落盘。
     */
    private String pkey = "NTK9aqcLmNFMWrpQ";

    private List<String> v1Keys = new ArrayList<>(List.of("NTK9aqcLmNFMWrpQ", "tOcYOap4Ty1l9Jzb"));

    public static MouflonSettings getInstance() {
        MouflonSettings local = instance;
        if (local == null) {
            synchronized (MouflonSettings.class) {
                local = instance;
                if (local == null) {
                    local = load();
                    instance = local;
                }
            }
        }
        return local;
    }

    public static synchronized void reload() {
        instance = null;
    }

    private static MouflonSettings load() {
        File file = configFile();
        if (file != null && file.exists()) {
            try {
                MouflonSettings s = new ObjectMapper().readValue(file, MouflonSettings.class);
                if (s.getXorKey() == null || s.getXorKey().isBlank()) s.setXorKey("tOcYOap4Ty1l9Jzb");
                if (s.getPkey() == null || s.getPkey().isBlank()) s.setPkey("NTK9aqcLmNFMWrpQ");
                if (s.v1Keys == null || s.v1Keys.isEmpty())
                    s.v1Keys = new ArrayList<>(List.of("NTK9aqcLmNFMWrpQ", "tOcYOap4Ty1l9Jzb"));
                LOG.info("MOUFLON settings loaded from {}", file.getAbsolutePath());
                return s;
            } catch (Exception e) {
                LOG.warn("Failed to load {}, using MOUFLON defaults: {}", file, e.getMessage());
            }
        }
        MouflonSettings defaults = new MouflonSettings();
        defaults.save();
        return defaults;
    }

    public void save() {
        File file = configFile();
        if (file == null) return;
        try {
            Files.createDirectories(file.getParentFile().toPath());
            new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT).writeValue(file, this);
        } catch (Exception e) {
            LOG.debug("Could not save MOUFLON settings", e);
        }
    }

    private static File configFile() {
        try {
            return new File(Config.getInstance().getConfigDir(), FILENAME);
        } catch (Exception e) {
            return null;
        }
    }

    public String getXorKey() {
        return xorKey;
    }

    public void setXorKey(String xorKey) {
        this.xorKey = xorKey;
    }

    public String getPkey() {
        return pkey;
    }

    public void setPkey(String pkey) {
        this.pkey = pkey;
    }

    /**
     * Persist a pkey/xorKey pairing that was verified against the live edge, so a working
     * combination survives a restart instead of having to be rediscovered.
     */
    public synchronized boolean adopt(String newPkey, String newXorKey) {
        if (newPkey == null || newPkey.isBlank() || newXorKey == null || newXorKey.isBlank()) {
            return false;
        }
        boolean changed = !newPkey.equals(pkey) || !newXorKey.equals(xorKey);
        this.pkey = newPkey.trim();
        this.xorKey = newXorKey.trim();
        if (changed) {
            save();
            LOG.info("MOUFLON pairing adopted: pkey={} xorKey={}", this.pkey, this.xorKey);
        }
        return changed;
    }

    public List<String> getV1Keys() {
        return v1Keys;
    }

    public void setV1Keys(List<String> v1Keys) {
        this.v1Keys = v1Keys;
    }

    @JsonIgnore
    public byte[] key16() {
        return Mouflon.deriveKey16(xorKey);
    }
}
