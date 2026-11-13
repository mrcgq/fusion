package ctbrec.ui.settings.api;

import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleListProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import lombok.Getter;

public class SimpleJoinedStringListProperty extends SimpleStringProperty implements ListChangeListener<String> {    
    private ObservableList<String> list;
    @Getter
    private String delimiter;
    private boolean updating = false;

    public SimpleJoinedStringListProperty(Object bean, String name, String delimiter, ObservableList<String> initialValue) {
        super(bean, name, String.join(delimiter, initialValue));
        this.delimiter = delimiter;   
        this.list = initialValue;
        initialValue.addListener(this);
    }


    @Override
    public void setValue(String newValue) {
        if (!updating) {
            try {
                updating = true;
                list.setAll(newValue.split(delimiter));
                super.setValue(newValue);
            } finally {
                updating = false;
            }
        }
    }

    @Override
    public void onChanged(Change<? extends String> c) {
        if (!updating) {
            try {
                updating = true;
                super.setValue(String.join(delimiter, list));
            } finally {
                updating = false;
            }
        }
    }
}
