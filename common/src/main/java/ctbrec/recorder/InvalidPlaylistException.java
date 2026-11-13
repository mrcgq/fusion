package ctbrec.recorder;

public class InvalidPlaylistException extends RuntimeException {
    public InvalidPlaylistException(String msg) {
        super(msg);
    }
}