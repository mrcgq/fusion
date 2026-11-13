package ctbrec.recorder.download;

public class ProcessExitedUncleanException extends RuntimeException {
    public ProcessExitedUncleanException(String msg) {
        super(msg);
    }
}