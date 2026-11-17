import ctbrec.recorder.download.hls.StripchatPlaylist;
import ctbrec.sites.stripchat.mouflon.Mouflon;
import ctbrec.sites.stripchat.mouflon.MouflonSettings;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * Replays the recorder's inner loop against a live StripChat stream using the deployed classes:
 * getNextSegments() body order == fetch playlist -> rewriteMediaPlaylistBody -> undecoded check
 * -> parse -> inheritQuery -> fetch. Also checks that the top rung is picked from the ladder.
 */
public class RecSim {

    static final String PROXY_HOST = "127.0.0.1";
    static final int PROXY_PORT = 10808;
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";
    static final Set<String> SEEN = new HashSet<>();

    static HttpClient client() {
        return HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT)))
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL).build();
    }

    static byte[] get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA).header("Referer", "https://stripchat.com/").GET().build();
        HttpResponse<byte[]> r = client().send(req, HttpResponse.BodyHandlers.ofByteArray());
        return r.statusCode() == 200 ? r.body() : null;
    }

    public static void main(String[] args) throws Exception {
        String sid = args.length > 0 ? args[0] : "158969122";
        System.out.println("configured pairing: pkey=" + MouflonSettings.getInstance().getPkey()
                + " xorKey=" + MouflonSettings.getInstance().getXorKey());
        System.out.println("StripchatPlaylist.pkey() resolves to: " + StripchatPlaylist.pkey(null));

        // ---- ladder check: the recorder must end up on the highest rung ----
        String master = "https://edge-hls.doppiocdn.net/hls/" + sid + "/master/" + sid + "_auto.m3u8"
                + "?playlistType=lowLatency";
        byte[] mb = get(master);
        if (mb == null) {
            System.out.println("master unreachable for sid=" + sid + " (pick another live room)");
            return;
        }
        String mbody = new String(mb);
        String variant = null;
        Matcher vm = Pattern.compile("^https://media-hls\\S+$", Pattern.MULTILINE).matcher(mbody);
        List<int[]> dims = new ArrayList<>();
        List<String> uris = new ArrayList<>();
        Matcher inf = Pattern.compile("#EXT-X-STREAM-INF:BANDWIDTH=(\\d+)[^\\n]*RESOLUTION=(\\d+)x(\\d+)"
                + "[^\\n]*\\n(\\S+)").matcher(mbody);
        while (inf.find()) {
            dims.add(new int[]{Integer.parseInt(inf.group(2)), Integer.parseInt(inf.group(3)),
                    Integer.parseInt(inf.group(1))});
            uris.add(inf.group(4));
        }
        System.out.println("ladder (as listed): " + describe(dims));
        // rank exactly like StreamSource.compareTo does
        Integer[] order = new Integer[dims.size()];
        for (int i = 0; i < order.length; i++) order[i] = i;
        Arrays.sort(order, (a, b) -> {
            long sa = (long) dims.get(a)[0] * dims.get(a)[1];
            long sb = (long) dims.get(b)[0] * dims.get(b)[1];
            if (sa != sb) return Long.compare(sa, sb);
            return Integer.compare(dims.get(a)[2], dims.get(b)[2]);
        });
        int best = order[order.length - 1];
        variant = uris.get(best);
        System.out.println("selected (top of sorted ladder): " + dims.get(best)[0] + "x"
                + dims.get(best)[1] + " @" + dims.get(best)[2]);

        if (!variant.toLowerCase().contains("psch=")) {
            // same suffix StripchatPlaylist.rewritePlaylistUrl() appends (Model-free form here)
            variant = variant + (variant.contains("?") ? "&" : "?") + "psch=v2&pkey="
                    + StripchatPlaylist.pkey(null);
        }
        System.out.println("variant url: " + variant);

        // ---- recording loop, same order as AbstractHlsDownload.getNextSegments ----
        long total = 0;
        int rounds = 0, roundsWithBytes = 0;
        for (int round = 0; round < 6; round++) {
            byte[] pb = get(variant);
            if (pb == null) {
                System.out.println("  round " + round + ": playlist fetch failed");
                Thread.sleep(2100);
                continue;
            }
            String body = new String(pb);
            boolean hadMouflon = Mouflon.hasV2Segments(body);
            body = StripchatPlaylist.rewriteMediaPlaylistBody(body);
            if (hadMouflon && StripchatPlaylist.undecodedMouflonSegments(body)) {
                System.out.println("  round " + round + ": ABORT - " + "MOUFLON segments not decodable"
                        + " (this is the loud failure the recorder now raises)");
                return;
            }
            List<String> segs = new ArrayList<>();
            Matcher im = Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"").matcher(body);
            String initUrl = im.find() ? im.group(1) : null;
            Matcher sm = Pattern.compile("^(https?://\\S+\\.mp4\\S*)$", Pattern.MULTILINE).matcher(body);
            while (sm.find()) {
                String u = sm.group(1);
                if (!u.contains("_init_")) segs.add(u);
            }
            int fresh = 0;
            long roundBytes = 0;
            List<String> notes = new ArrayList<>();
            ExecutorService ex = Executors.newFixedThreadPool(5);
            List<String> want = new ArrayList<>();
            if (initUrl != null && SEEN.add("INIT")) want.add(initUrl);
            for (String s : segs) if (SEEN.add(s)) want.add(s);
            List<Future<long[]>> fs = new ArrayList<>();
            for (String u : want) {
                final String finalUrl = StripchatPlaylist.inheritQuery(variant, u);
                fs.add(ex.submit(() -> {
                    try {
                        byte[] b = get(finalUrl);
                        return new long[]{b == null ? -1 : b.length};
                    } catch (Exception e) {
                        return new long[]{-1};
                    }
                }));
            }
            for (int i = 0; i < fs.size(); i++) {
                long n = fs.get(i).get()[0];
                if (n > 0) {
                    fresh++;
                    roundBytes += n;
                }
                notes.add("        " + (n > 0 ? "200" : "FAIL") + " " + String.format("%9d", Math.max(n, 0))
                        + "B  " + name(want.get(i)));
            }
            ex.shutdownNow();
            total += roundBytes;
            rounds++;
            if (roundBytes > 0) roundsWithBytes++;
            System.out.printf("  round %d: segLines=%d fresh=%d newBytes=%,dB (mouflon=%s)%n",
                    round, segs.size(), fresh, roundBytes, hadMouflon);
            for (String s : notes) System.out.println(s);
            Thread.sleep(2100);
        }
        System.out.printf("%nROUNDS=%d withBytes=%d TOTAL=%,dB%n", rounds, roundsWithBytes, total);
        System.out.println(roundsWithBytes >= 3 && total > 1_000_000
                ? "=> STRIPCHAT SUSTAINED CAPTURE PASS (native XOR-key path)"
                : "=> sustained capture NOT proven");
    }

    static String describe(List<int[]> dims) {
        StringBuilder sb = new StringBuilder();
        for (int[] d : dims) sb.append(d[0]).append('x').append(d[1]).append(' ');
        return sb.toString().trim();
    }

    static String name(String u) {
        int s = u.lastIndexOf('/');
        String f = s >= 0 ? u.substring(s + 1) : u;
        int q = f.indexOf('?');
        return q > 0 ? f.substring(0, q) : f;
    }
}
