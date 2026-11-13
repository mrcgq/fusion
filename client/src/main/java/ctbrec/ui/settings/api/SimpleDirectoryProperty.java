package ctbrec.ui.settings.api;

import javafx.beans.property.SimpleStringProperty;

public class SimpleDirectoryProperty extends SimpleStringProperty {

    public SimpleDirectoryProperty(Object bean, String name, String initialValue) {
        super(bean, name, initialValue);
    }

}
