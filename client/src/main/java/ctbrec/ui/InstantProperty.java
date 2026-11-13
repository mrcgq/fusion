package ctbrec.ui;

import java.time.Instant;

import javafx.beans.property.ObjectPropertyBase;

public class InstantProperty extends ObjectPropertyBase<Instant> {

    @Override
    public Object getBean() {
        return get();
    }

    @Override
    public String getName() {
        return "Instant";
    }
}
