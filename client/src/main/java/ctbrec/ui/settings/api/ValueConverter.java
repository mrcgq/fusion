package ctbrec.ui.settings.api;

public interface ValueConverter {

    Object convertTo(Object a);
    Object convertFrom(Object b);
}
