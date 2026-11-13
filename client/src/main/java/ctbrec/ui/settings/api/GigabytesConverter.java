package ctbrec.ui.settings.api;

public class GigabytesConverter implements ValueConverter {

    private static final int ONE_GIB_IN_BYTES = 1024 * 1024 * 1024;

    @Override
    public Object convertTo(Object a) {
        long input = (long) a;
        return input / ONE_GIB_IN_BYTES;
    }

    @Override
    public Object convertFrom(Object b) {
        long gibiBytes = (long) b;
        return gibiBytes * ONE_GIB_IN_BYTES;
    }

}
