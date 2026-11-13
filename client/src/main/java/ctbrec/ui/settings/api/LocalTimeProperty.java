package ctbrec.ui.settings.api;

import java.time.LocalTime;

import javafx.beans.property.SimpleObjectProperty;

public class LocalTimeProperty extends SimpleObjectProperty<LocalTime> {

    public LocalTimeProperty(Object bean, String name, LocalTime initialValue) {
        super(bean, name, initialValue);
    }

}
