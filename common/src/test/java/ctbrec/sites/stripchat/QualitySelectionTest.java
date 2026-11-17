package ctbrec.sites.stripchat;

import ctbrec.recorder.download.StreamSource;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ladder ranking, fed with the variant tables really observed on Chaturbate and Stripchat.
 */
class QualitySelectionTest {

    private static StreamSource src(int w, int h, int kbit) {
        StreamSource s = new StreamSource();
        s.setWidth(w);
        s.setHeight(h);
        s.setBandwidth(kbit * 1000);
        s.setMediaPlaylistUrl("https://example.invalid/" + w + "x" + h + "/m3u8");
        return s;
    }

    private static StreamSource top(List<StreamSource> in) {
        List<StreamSource> s = new ArrayList<>(in);
        Collections.sort(s);
        return s.get(s.size() - 1);
    }

    @Test
    void stripchatAutoMasterIsDescendingButTopIsStillSource() {
        // observed: #EXT-X-STREAM-INF order is source(1920x1080) 720p 480p 240p
        List<StreamSource> ladder = new ArrayList<>(List.of(
                src(1920, 1080, 6929),   // listed first by the edge
                src(1280, 720, 2649),
                src(854, 480, 900),
                src(426, 240, 400)));
        StreamSource best = top(ladder);
        assertEquals(1080, best.getHeight(), "must take the source rung, not the last listed one");
        assertEquals(1920, best.getWidth());
    }

    @Test
    void verticalLadderIsRankedByPixelsNotHeight() {
        // observed on Stripchat: source 720x960, 480p 360x480, 240p 180x240
        List<StreamSource> ladder = new ArrayList<>(List.of(
                src(720, 960, 2400),
                src(360, 480, 900),
                src(180, 240, 400)));
        StreamSource best = top(ladder);
        assertEquals(960, best.getHeight());
        assertEquals(720, best.getWidth());
    }

    @Test
    void sameHeightFallsBackToBandwidth() {
        // observed on Chaturbate: two rungs both 1280x720, 3096k and 4096k
        List<StreamSource> ladder = new ArrayList<>(List.of(
                src(1280, 720, 3096),
                src(1280, 720, 4096),
                src(1920, 1080, 5128)));
        StreamSource best = top(ladder);
        assertEquals(1080, best.getHeight());
        List<StreamSource> twoRungs = new ArrayList<>(List.of(src(1280, 720, 3096), src(1280, 720, 4096)));
        assertEquals(4096_000, top(twoRungs).getBandwidth());
    }

    @Test
    void chaturbateLadderTakesTopRung() {
        // observed: 360p 896, 540p 1696, 720p 3096, 1080p 5128
        List<StreamSource> ladder = new ArrayList<>(List.of(
                src(640, 360, 896), src(960, 540, 1696), src(1280, 720, 3096), src(1920, 1080, 5128)));
        Collections.sort(ladder);
        assertEquals(List.of(360, 540, 720, 1080),
                ladder.stream().map(StreamSource::getHeight).toList());
    }

    @Test
    void singleBitrateSourceIsNotRankedAboveARealRung() {
        // width/height 0 means "master itself is the media playlist"
        StreamSource single = src(0, 0, 1200);
        List<StreamSource> ladder = new ArrayList<>(List.of(single, src(1280, 720, 2649)));
        StreamSource best = top(ladder);
        assertEquals(720, best.getHeight());
        assertTrue(single.getQualityScore() < src(1280, 720, 2649).getQualityScore());
    }

    @Test
    void originSentinelStillRanksHighest() {
        StreamSource origin = new StreamSource();
        origin.setHeight(StreamSource.ORIGIN);
        origin.setWidth(StreamSource.ORIGIN);
        origin.setBandwidth(0);
        List<StreamSource> ladder = new ArrayList<>(List.of(origin, src(1920, 1080, 5128)));
        // ORIGIN must not be demoted by the pixel ranking path
        Collections.sort(ladder);
        assertEquals(StreamSource.ORIGIN, ladder.get(ladder.size() - 1).getHeight());
    }

    /**
     * Ground truth captured from the running player (pair.py): the hash the playlist carries and
     * the plaintext name the player actually requested. If these ever stop matching, the XOR key
     * rotated and the recorder will report undecodable segments instead of silently 404ing.
     */
    @Test
    void capturedPlayerPairsDecodeWithTheConfiguredKey() {
        byte[] k16 = ctbrec.sites.stripchat.mouflon.Mouflon.deriveKey16("tOcYOap4Ty1l9Jzb");
        assertEquals("1838c78f543606e6de5fc81167f36e5a",
                java.util.HexFormat.of().formatHex(k16));
        assertEquals("uqkIsoRvrr6UIHFT",
                ctbrec.sites.stripchat.mouflon.Mouflon.decryptV2Hash("gDosrLE5fLsCJVZdixsmUb", k16));
        assertEquals("WTAkTaH1aLT5J8tL",
                ctbrec.sites.stripchat.mouflon.Mouflon.decryptV2Hash("gFascLkw5E/etTXBA5Gy2T", k16));
    }

    /** A playlist fetched with a decoy pkey looks valid but nothing decodes: that must be detected. */
    @Test
    void decoyPlaylistIsReportedAsUndecodable() {
        String real = "#EXTM3U\n#EXT-X-MOUFLON:PSCH:v2:NTK9aqcLmNFMWrpQ\n#EXTINF:2.000,\n"
                + "#EXT-X-MOUFLON:URI:https://media-hls.doppiocdn.net/b-hls-06/285524464/"
                + "285524464_762_gDosrLE5fLsCJVZdixsmUb_1794906248_part0.mp4\n"
                + "https://media-hls.doppiocdn.net/b-hls-06/media.mp4\n";
        assertEquals(1, ctbrec.sites.stripchat.mouflon.Mouflon.decodedV2Count(real, "tOcYOap4Ty1l9Jzb"));

        // same shape, but obfuscated with a different key: decodes to nothing
        String decoy = real.replace("gDosrLE5fLsCJVZdixsmUb", "Q/I/pxNAypcGulIVKnyb0+");
        assertEquals(0, ctbrec.sites.stripchat.mouflon.Mouflon.decodedV2Count(decoy, "tOcYOap4Ty1l9Jzb"));
    }
}
