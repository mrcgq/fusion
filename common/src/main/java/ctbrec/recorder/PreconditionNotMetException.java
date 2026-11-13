package ctbrec.recorder;

public class PreconditionNotMetException extends RuntimeException {
    public PreconditionNotMetException() {
        super("Precondition not met");
    }

    public PreconditionNotMetException(String message) {
        super(message);
    }

    public PreconditionNotMetException(String message, Throwable t) {
        super(message, t);
    }
}
