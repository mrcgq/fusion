package ctbrec.ui.settings.api;

import ctbrec.ui.controls.range.Range;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

public class SimpleRangeProperty<T> extends SimpleObjectProperty<T> {

    private Range<T> range;
    private SimpleObjectProperty<T> lowProperty;
    private SimpleObjectProperty<T> highProperty;
    private String lowKey;
    private String highKey;

    public SimpleRangeProperty(Range<T> range, String lowKey, String highKey, T low, T high) {
        super(null, lowKey);
        this.range = range;
        this.lowKey = lowKey;
        this.highKey = highKey;
        lowProperty = new SimpleObjectProperty<>(low);
        highProperty = new SimpleObjectProperty<>(high);
    }

    public Range<T> getRange() {
        return range;
    }

    public ObjectProperty<T> lowProperty() {
        return lowProperty;
    }

    public ObjectProperty<T> highProperty() {
        return highProperty;
    }

    public String getLowKey() {
        return lowKey;
    }

    public String getHighKey() {
        return highKey;
    }
}
