package ctbrec.sites.stripchat;

import ctbrec.Config;
import ctbrec.recorder.download.hls.SegmentPlaylist;

import java.util.ArrayList;
import java.util.List;

/**
 * 段序恢复（MOUFLON PSCH v2 的 URI 行序还原）：
 * 播放列表里 MOUFLON URI 行的物理顺序被打乱，真实播放顺序按
 *   (段号, part 号) 稳定排序后，以 seed 首字符 ASCII 奇偶决定交错方向。
 * 该纯函数供调试与离线校验用；在线录制路径依赖服务器按 msn/part 递进返回，
 * 物理序即播放序，无需重排。若实测某边缘节点乱序，可在
 * StripchatFfmpegHlsDownload 中接入本函数（settings.stripchatDemangle 控制）。
 */
public final class StripchatSegmentOrder {

    private StripchatSegmentOrder() { }

    public static List<SegmentPlaylist.Segment> restore(List<SegmentPlaylist.Segment> segs, String seed) {
        if (segs == null || segs.size() < 2) return segs == null ? new ArrayList<>() : new ArrayList<>(segs);
        List<SegmentPlaylist.Segment> sorted = new ArrayList<>(segs);
        sorted.sort((a, b) -> {
            long ka = key(a.url), kb = key(b.url);
            return Long.compare(ka, kb);
        });
        int offset = StripchatEdgeResolver.seedOffset(seed);
        List<SegmentPlaylist.Segment> even = new ArrayList<>();
        List<SegmentPlaylist.Segment> odd = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            if (((i + offset) & 1) == 0) even.add(sorted.get(i));
            else odd.add(sorted.get(i));
        }
        List<SegmentPlaylist.Segment> out = new ArrayList<>(sorted.size());
        int ei = 0, oi = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (((i + offset) & 1) == 0) out.add(even.get(ei++));
            else out.add(odd.get(oi++));
        }
        return out;
    }

    /** 复合排序键：epoch*100 + part（epoch 从 _<digits>_part<N> 抽取）。 */
    static long key(String url) {
        if (url == null) return 0L;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("_(\\d{6,})_part(\\d+)\\.[a-z0-9]+(?:\\?|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(url);
        if (m.find()) {
            long epoch = Long.parseLong(m.group(1));
            int part = Integer.parseInt(m.group(2));
            return epoch * 100L + part;
        }
        return 0L;
    }

    static Config config() {
        return Config.getInstance();
    }
}
