package ctbrec.recorder;

public class InvalidTrackLengthException extends RuntimeException {
    public InvalidTrackLengthException(String msg) {
        super(msg);
    }
}