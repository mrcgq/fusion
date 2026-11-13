package ctbrec.recorder.download.hls;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class SegmentPlaylist {
    public String url;
    public int seq = 0;
    public float totalDuration = 0;
    public float avgSegDuration = 0;
    public float targetDuration = 0;
    public List<Segment> segments = new ArrayList<>();
    public boolean encrypted = false;
    public String encryptionMethod = "AES-128";
    public String encryptionKeyUrl;

    public SegmentPlaylist(String url) {
        this.url = url;
    }

    public static class Segment {
        public String url;
        public String prefix;
        public File targetFile;
        public float duration;

        public Segment(String url, float duration) {
            this.url = url;
            this.duration = duration;
        }
    }
}
