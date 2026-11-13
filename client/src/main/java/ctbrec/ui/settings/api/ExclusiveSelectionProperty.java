package ctbrec.ui.settings.api;

import javafx.beans.property.SimpleBooleanProperty;

public class ExclusiveSelectionProperty extends SimpleBooleanProperty {

    private String optionA;
    private String optionB;

    public ExclusiveSelectionProperty(Object bean, String name, boolean value, String optionA, String optionB) {
        super(bean, name, value);
        this.optionA = optionA;
        this.optionB = optionB;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }
}
