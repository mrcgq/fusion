package ctbrec;

public class UnsupportedOperatingSystemException extends RuntimeException {

    public UnsupportedOperatingSystemException(String os) {
        super("Unsupported operating system " + os);
    }

}
