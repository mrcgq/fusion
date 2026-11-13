package ctbrec.sites.stripchat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StripchatFixTest {

    static final String LL_PLAYLIST = "#EXTM3U\n"
            + "#EXT-X-VERSION:6\n"
            + "#EXT-X-MOUFLON:PSCH:v2:NTK9aqcLmNFMWrpQ\n"
            + "#EXT-X-TARGETDURATION:2\n"
            + "#EXT-X-SERVER-CONTROL:CAN-BLOCK-RELOAD=YES,PART-HOLD-BACK=1.500\n"
            + "#EXT-X-PART-INF:PART-TARGET=0.500\n"
            + "#EXT-X-INDEPENDENT-SEGMENTS\n"
            + "#EXT-X-MEDIA-SEQUENCE:8822\n"
            + "#EXT-X-MAP:URI=\"https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834_init_snhriKjI7wD2YYX8.mp4\"\n"
            + "#EXT-X-MOUFLON:URI:https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834_8822_wOrUcHgsICMiIXGBxuxfnc_1794561984_part0.mp4\n"
            + "#EXT-X-PART:DURATION=0.500,URI=\"https://media-hls.doppiocdn.net/b-hls-09/media.mp4\",INDEPENDENT=YES\n"
            + "#EXT-X-MOUFLON:URI:https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834_8822_wOrUcHgsICMiIXGBxuxfnc_1794561984_part1.mp4\n"
            + "#EXT-X-PART:DURATION=0.500,URI=\"https://media-hls.doppiocdn.net/b-hls-09/media.mp4\"\n"
            + "#EXT-X-MOUFLON:URI:https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834_8823_aaa111_1794561986_part0.mp4\n"
            + "#EXT-X-PART:DURATION=0.500,URI=\"https://media-hls.doppiocdn.net/b-hls-09/media.mp4\"\n"
            + "#EXT-X-MOUFLON:URI:https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834_8823_aaa111_1794561986_part1.mp4\n"
            + "#EXT-X-PART:DURATION=0.500,URI=\"https://media-hls.doppiocdn.net/b-hls-09/media.mp4\"\n";

    @Test
    void rewriteReplacesPlaceholderSegmentsWithMouflonUris() {
        String out = ctbrec.recorder.download.hls.StripchatPlaylist.rewriteMediaPlaylistBody(LL_PLAYLIST);
        assertFalse(out.contains("media.mp4"), "placeholder must be gone: " + out);
        assertTrue(out.contains("8822_wOrUcHgsICMiIXGBxuxfnc_1794561984_part0.mp4"));
        assertTrue(out.contains("8822_wOrUcHgsICMiIXGBxuxfnc_1794561984_part1.mp4"));
        assertTrue(out.contains("8823_aaa111_1794561986_part0.mp4"));
        assertTrue(out.contains("8823_aaa111_1794561986_part1.mp4"));
        int extinf = out.split("#EXTINF:", -1).length - 1;
        assertEquals(4, extinf);
    }

    @Test
    void nonMouflonPlaylistPassesThroughUnchanged() {
        String plain = "#EXTM3U\n#EXTINF:2.0,\nseg.ts\n";
        assertSame(plain, ctbrec.recorder.download.hls.StripchatPlaylist.rewriteMediaPlaylistBody(plain));
    }

    @Test
    void inheritQueryKeepsPschAndPkey() {
        String pl = "https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834.m3u8?playlistType=lowLatency&psch=v2&pkey=NTK9aqcLmNFMWrpQ&_HLS_msn=8729";
        String seg = "https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834_8822_x_1_part0.mp4";
        String r = ctbrec.recorder.download.hls.StripchatPlaylist.inheritQuery(pl, seg);
        assertTrue(r.contains("psch=v2"));
        assertTrue(r.contains("pkey=NTK9aqcLmNFMWrpQ"));
        assertEquals(seg + "?psch=v2", ctbrec.recorder.download.hls.StripchatPlaylist.inheritQuery(pl, seg + "?psch=v2"));
    }

    @Test
    void pschSeedExtraction() {
        assertEquals("NTK9aqcLmNFMWrpQ", StripchatEdgeResolver.pschSeed(LL_PLAYLIST));
    }

    @Test
    void demangleOrdersByEpochThenPart() {
        List<String> segs = List.of(
                "https://h/a_8823_b_1794561986_part1.mp4",
                "https://h/a_8822_b_1794561984_part1.mp4",
                "https://h/a_8822_b_1794561984_part0.mp4",
                "https://h/a_8823_b_1794561986_part0.mp4");
        List<String> out = StripchatEdgeResolver.demanglePschSegments(segs, "NTK9aqcLmNFMWrpQ");
        assertEquals(4, out.size());
        assertTrue(out.get(0).endsWith("part0.mp4"));
        assertTrue(out.get(1).endsWith("part1.mp4"));
    }

    @Test
    void extractIdFromBothPathShapes() {
        assertEquals(53015834L, StripchatEdgeResolver.extractId("https://edge-hls.saawsedge.com/hls/53015834/master/53015834.m3u8"));
        assertEquals(53015834L, StripchatEdgeResolver.extractId("https://media-hls.doppiocdn.net/b-hls-09/53015834/53015834.m3u8?psch=v2"));
    }

    @Test
    void countMouflonSegments() {
        assertEquals(4, ctbrec.recorder.download.hls.StripchatPlaylist.countMouflonSegments(LL_PLAYLIST));
    }
}
