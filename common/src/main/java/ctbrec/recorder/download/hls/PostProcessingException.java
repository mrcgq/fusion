package ctbrec.recorder.download.hls;

public class PostProcessingException extends RuntimeException {

    public PostProcessingException(String msg) {
        super(msg);
    }

    public PostProcessingException(Exception cause) {
        super(cause);
    }

}
