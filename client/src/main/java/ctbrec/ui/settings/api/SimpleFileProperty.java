package ctbrec.ui.settings.api;

import javafx.beans.property.SimpleStringProperty;

public class SimpleFileProperty extends SimpleStringProperty {

    public SimpleFileProperty(Object bean, String name, String initialValue) {
        super(bean, name, initialValue);
    }

}
