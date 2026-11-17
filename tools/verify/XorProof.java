import ctbrec.sites.stripchat.mouflon.Mouflon;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/**
 * End-to-end check of the DEPLOYED classes only (no browser, no python):
 * master -> choose pkey -> variant playlist -> Mouflon.normalize -> fetch init + segments.
 */
public class XorProof {

    static final String PROXY_HOST = "127.0.0.1";
    static final int PROXY_PORT = 10808;
    static final String XOR_KEY = "tOcYOap4Ty1l9Jzb";
    static final String UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    static HttpClient client() {
        return HttpClient.newBuilder()
                .proxy(ProxySelector.of(new InetSocketAddress(PROXY_HOST, PROXY_PORT)))
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    static byte[] get(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", UA)
                .header("Referer", "https://stripchat.com/")
                .GET().build();
        HttpResponse<byte[]> r = client().send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (r.statusCode() != 200) {
            return null;
        }
        return r.body();
    }

    public static void main(String[] args) throws Exception {
        String sid = args.length > 0 ? args[0] : "248813265";
        String[] hosts = {"edge-hls.doppiocdn.net", "edge-hls.doppiocdn.org",
                "edge-hls.doppiocdn.com", "edge-hls.doppiocdn.media"};
        byte[] mb = null;
        String master = null;
        for (String h : hosts) {
            master = "https://" + h + "/hls/" + sid + "/master/" + sid + "_auto.m3u8?playlistType=lowLatency";
            mb = get(master);
            if (mb != null) {
                System.out.println("edge host = " + h);
                break;
            }
        }
        if (mb == null) {
            System.out.println("master fetch failed on all hosts");
            return;
        }
        String mbody = new String(mb);
        List<String> seeds = Mouflon.pschSeeds(mbody);
        System.out.println("psch seeds in master = " + seeds.size());
        Matcher vm = Pattern.compile("^https://media-hls\\S+$", Pattern.MULTILINE).matcher(mbody);
        if (!vm.find()) {
            System.out.println("no variant");
            return;
        }
        String variant = vm.group();

        System.out.println("\nkey16 from '" + XOR_KEY + "' = "
                + java.util.HexFormat.of().formatHex(Mouflon.deriveKey16(XOR_KEY)));

        long bestBytes = 0;
        String bestPkey = null;
        for (String pk : seeds) {
            String url = variant + (variant.contains("?") ? "&" : "?") + "psch=v2&pkey=" + pk;
            byte[] pb = get(url);
            if (pb == null) continue;
            String pl = new String(pb);
            if (!pl.contains("#EXTINF") || pl.contains("MOUFLON-ADVERT") || pl.contains("/cpa/")) {
                continue;
            }
            int total = Mouflon.hasV2Segments(pl) ? countMouflon(pl) : 0;
            int decodable = Mouflon.decodedV2Count(pl, XOR_KEY);
            if (decodable == 0) {
                System.out.printf("  pkey=%-17s playlist=REAL mouflon=%2d decodable=%d  -> decoy%n", pk, total, decodable);
                continue;
            }

            // this is exactly what AbstractHlsDownload now does before parsing
            String fixed = Mouflon.normalize(pl, XOR_KEY);
            List<String> segUrls = new ArrayList<>();
            Matcher im = Pattern.compile("#EXT-X-MAP:URI=\"([^\"]+)\"").matcher(fixed);
            if (im.find()) {
                String iu = im.group(1);
                segUrls.add(iu + (iu.contains("?") ? "&" : "?") + "psch=v2&pkey=" + pk);
            }
            Matcher sm = Pattern.compile("^(https://media-hls\\S+\\.mp4\\S*)$", Pattern.MULTILINE).matcher(fixed);
            while (sm.find() && segUrls.size() < 7) {
                String u = sm.group(1);
                if (u.contains("_init_") || u.contains("psch=")) continue;
                segUrls.add(u + (u.contains("?") ? "&" : "?") + "psch=v2&pkey=" + pk);
            }
            ExecutorService ex = Executors.newFixedThreadPool(6);
            List<Future<long[]>> fs = new ArrayList<>();
            for (String u : segUrls) {
                fs.add(ex.submit(() -> {
                    try {
                        byte[] b = get(u);
                        return new long[]{b == null ? -1 : b.length};
                    } catch (Exception e) {
                        return new long[]{-1};
                    }
                }));
            }
            long bytes = 0;
            int ok = 0;
            List<String> report = new ArrayList<>();
            for (int i = 0; i < fs.size(); i++) {
                long n = fs.get(i).get()[0];
                if (n > 0) {
                    ok++;
                    bytes += n;
                }
                report.add("      " + (n > 0 ? "200 " : "FAIL") + " " + String.format("%9d", Math.max(n, 0))
                        + "B  " + tail(segUrls.get(i)));
            }
            ex.shutdownNow();
            System.out.printf("  pkey=%-17s playlist=REAL mouflon=%2d decodable=%d  fetched %d/%d  %,dB%n",
                    pk, total, decodable, ok, segUrls.size(), bytes);
            for (String r : report) System.out.println(r);
            if (bytes > bestBytes) {
                bestBytes = bytes;
                bestPkey = pk;
            }
        }

        System.out.println("\nBEST pkey=" + bestPkey + "  total real bytes=" + bestBytes);
        System.out.println(bestBytes > 100_000
                ? "=> DEPLOYED JAVA XOR-KEY PATH WORKS (offline, no browser)"
                : "=> java path still not fetching");
    }

    static int countMouflon(String body) {
        int n = 0, i = -1;
        while ((i = body.indexOf("#EXT-X-MOUFLON:URI:", i + 1)) > 0) n++;
        return n;
    }

    static String tail(String u) {
        int s = u.lastIndexOf('/');
        String f = s >= 0 ? u.substring(s + 1) : u;
        int q = f.indexOf('?');
        return q > 0 ? f.substring(0, q) : f;
    }
}
