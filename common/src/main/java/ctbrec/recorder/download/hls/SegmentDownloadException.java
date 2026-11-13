package ctbrec.recorder.download.hls;

public class SegmentDownloadException extends RuntimeException {

    public SegmentDownloadException(Exception e) {
        super("Segment download failed", e);
    }

}
