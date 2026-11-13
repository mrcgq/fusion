package ctbrec.recorder.download.hls;

public class NoStreamFoundException extends RuntimeException {
    public NoStreamFoundException(String msg) {
        super(msg);
    }
}
