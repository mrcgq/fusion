package ctbrec.recorder.download.hls;

import java.net.SocketTimeoutException;

public class PlaylistTimeoutException extends RuntimeException {

    public PlaylistTimeoutException(SocketTimeoutException e) {
        super(e);
    }

}
