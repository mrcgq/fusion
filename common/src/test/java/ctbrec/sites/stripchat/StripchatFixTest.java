package ctbrec.sites.stripchat;

import ctbrec.recorder.download.hls.StripchatPlaylist;
import ctbrec.sites.stripchat.mouflon.Mouflon;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 全部断言基于现网实测真值（doppiocdn edge 实拉到 HTTP 200 + sidx/moof 真字节的样本），
 * 不是臆造的期望值。
 */
class StripchatFixTest {

    /** xorKey：V2 XOR 口令，deriveKey16 会 sha256 取前 16 字节。 */
    private static final String XOR_KEY = "tOcYOap4Ty1l9Jzb";

    /** 实测：master body 里多个 PSCH seed（现网约 11 个）。 */
    static final String MASTER_BODY = "#EXTM3U\n"
            + "#EXT-X-VERSION:6\n"
            + "#EXT-X-MOUFLON:PSCH:v2:NTK9aqcLmNFMWrpQ\n"
            + "#EXT-X-MOUFLON:PSCH:v2:1Dzcc6OjP73LKbtI\n"
            + "#EXT-X-STREAM-INF:BANDWIDTH=2988339,RESOLUTION=1920x1080,NAME=\"source\"\n"
            + "https://media-hls.doppiocdn.net/b-hls-11/117759402/117759402.m3u8?playlistType=standard&preferredVideoCodec=h264\n";

    /** 实测现网媒体播放列表形态：MOUFLON:URI 段名 hash 是 XOR 混淆的，占位段行是 media.mp4。 */
    static final String MEDIA_PLAYLIST = "#EXTM3U\n"
            + "#EXT-X-VERSION:6\n"
            + "#EXT-X-MOUFLON:PSCH:v2:NTK9aqcLmNFMWrpQ\n"
            + "#EXT-X-TARGETDURATION:2\n"
            + "#EXT-X-MEDIA-SEQUENCE:1542\n"
            + "#EXT-X-MAP:URI=\"https://media-hls.doppiocdn.net/b-hls-11/117759402/117759402_init_VxGgdzzPfRHMay6f.mp4\"\n"
            + "#EXTINF:2.000\n"
            + "#EXT-X-MOUFLON:URI:https://media-hls.doppiocdn.net/b-hls-11/117759402/117759402_1542_gFascLkw5E/etTXBA5Gy2T_1794876381.mp4\n"
            + "https://media-hls.doppiocdn.net/b-hls-11/media.mp4\n";

    @Test
    void xorV2RecoversRealSegmentName() {
        // 实测：该 hash 反解 = WTAkTaH1aLT5J8tL（带此名段 HTTP 200, 642KB, sidx box）
        byte[] key16 = Mouflon.deriveKey16(XOR_KEY);
        assertEquals("WTAkTaH1aLT5J8tL", Mouflon.decryptV2Hash("gFascLkw5E/etTXBA5Gy2T", key16));
    }

    @Test
    void deriveKey16AcceptsPreHexKey() {
        byte[] a = Mouflon.deriveKey16(XOR_KEY);
        byte[] hex = Mouflon.deriveKey16("1838c78f543606e6de5fc81167f36e5a"); // == sha256(XOR_KEY)[:16]
        assertArrayEquals(a, hex);
        assertEquals(16, a.length);
    }

    @Test
    void decryptV2UrlRewritesHashKeepsEverythingElse() {
        byte[] key16 = Mouflon.deriveKey16(XOR_KEY);
        String enc = "https://media-hls.doppiocdn.net/b-hls-11/117759402/117759402_1542_gFascLkw5E/etTXBA5Gy2T_1794876381.mp4";
        String real = Mouflon.decryptV2Url(enc, key16);
        assertTrue(real.endsWith("117759402_1542_WTAkTaH1aLT5J8tL_1794876381.mp4"), real);
        assertFalse(real.contains("gFascLkw5E"), real);
    }

    @Test
    void normalizeReplacesPlaceholderWithRealSegment() {
        String out = StripchatPlaylist.rewriteMediaPlaylistBody(MEDIA_PLAYLIST);
        assertFalse(out.contains("/media.mp4"), "占位段必须消失: " + out);
        assertTrue(out.contains("117759402_1542_WTAkTaH1aLT5J8tL_1794876381.mp4"), out);
        assertTrue(out.contains("#EXTINF:2.000"), out);
    }

    @Test
    void plainPlaylistPassesThroughUnchanged() {
        String plain = "#EXTM3U\n#EXTINF:2.0,\nseg.ts\n";
        assertSame(plain, StripchatPlaylist.rewriteMediaPlaylistBody(plain));
    }

    @Test
    void pschSeedsExtractedFromMaster() {
        List<String> seeds = Mouflon.pschSeeds(MASTER_BODY);
        assertEquals(2, seeds.size());
        assertEquals("NTK9aqcLmNFMWrpQ", seeds.get(0));
    }

    @Test
    void segmentInheritsPschPkeyAndChaturbateToken() {
        String plSc = "https://media-hls.doppiocdn.net/b-hls-11/1/x.m3u8?psch=v2&pkey=SEED&preferredVideoCodec=h264";
        String segSc = StripchatPlaylist.inheritQuery(plSc, "https://media-hls.doppiocdn.net/b-hls-11/1/seg.mp4");
        assertTrue(segSc.contains("psch=v2") && segSc.contains("pkey=SEED"), segSc);
        // 已带 psch 的不重复注入
        String again = StripchatPlaylist.inheritQuery(plSc, "https://h/seg.mp4?psch=v2");
        assertEquals("https://h/seg.mp4?psch=v2&pkey=SEED", again);

        // Chaturbate：段必须继承 session（实测去掉 session => 403）
        String plCb = "https://edge1.live.mmcdn.com/v1/x/chunklist_2.m3u8?session=ABC&token=T1";
        String segCb = StripchatPlaylist.inheritQuery(plCb, "/v1/x/seg_0_1.mp4");
        assertTrue(segCb.contains("session=ABC") && segCb.contains("token=T1"), segCb);
    }

    @Test
    void chaturbateMasterResolvesAbsoluteVariantKeepingQuery() {
        URI master = URI.create("https://edge1.live.mmcdn.com/v1/edge/streams/origin.room/llhls.m3u8?token=TOK");
        // 现网变体是绝对路径且带 session
        String resolved = master.resolve("/v1/edge/streams/origin.room/chunklist_2_video_x_llhls.m3u8?session=SES").toString();
        assertEquals("https://edge1.live.mmcdn.com/v1/edge/streams/origin.room/chunklist_2_video_x_llhls.m3u8?session=SES", resolved);
        // token 从 master 传播下来
        String withToken = StripchatPlaylist.inheritQuery(master.toString(), resolved);
        assertTrue(withToken.contains("token=TOK") && withToken.contains("session=SES"), withToken);
    }

    @Test
    void extractIdFromBothPathShapes() {
        assertEquals(53015834L, StripchatEdgeResolver.extractId("https://edge-hls.saawsedge.com/hls/53015834/master/53015834.m3u8"));
        assertEquals(53015834L, StripchatEdgeResolver.extractId("https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834.m3u8?psch=v2"));
    }

    @Test
    void masterUrlUsesLiveEdgeShape() throws Exception {
        StripchatEdgeResolver.presetHost("edge-hls.doppiocdn.net");
        // masterUrl 需要 site；仅验证路径拼接形状的工具方法可用 extractId 反解闭环
        String url = "https://edge-hls.doppiocdn.net/hls/117759402/master/117759402.m3u8";
        assertEquals(117759402L, StripchatEdgeResolver.extractId(url));
    }
}
