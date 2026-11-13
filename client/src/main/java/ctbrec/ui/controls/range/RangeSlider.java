package ctbrec.ui.controls.range;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Orientation;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

public class RangeSlider<T extends Number> extends Control  {

    private static final String DEFAULT_STYLE_CLASS = "rangeslider";

    private Range<T> range;
    private ObjectProperty<T> low;
    private ObjectProperty<T> high;
    private boolean showTickMarks = false;
    private boolean showTickLabels = false;
    private Orientation orientation = Orientation.HORIZONTAL;

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public RangeSlider(Range<T> range) {
        this.range = range;
        low = new SimpleObjectProperty(getMinimum());
        high = new SimpleObjectProperty(getMaximum());
        getStyleClass().setAll(DEFAULT_STYLE_CLASS);
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new RangeSliderSkin(this, new RangeSliderBehavior<>(this));
    }

    @Override
    public String getUserAgentStylesheet() {
        return RangeSlider.class.getResource("rangeslider.css").toExternalForm();
    }

    public ObjectProperty<T> getLow() {
        return low;
    }

    public void setLow(T newPosition) {
        low.set(newPosition);
    }

    public ObjectProperty<T> getHigh() {
        return high;
    }

    public void setHigh(T newPosition) {
        this.high.set(newPosition);
    }

    public T getMinimum() {
        return range.getMinimum();
    }

    public T getMaximum() {
        return range.getMaximum();
    }

    public boolean isShowTickMarks() {
        return showTickMarks;
    }

    public void setShowTickMarks(boolean showTickMarks) {
        this.showTickMarks = showTickMarks;
    }

    public boolean isShowTickLabels() {
        return showTickLabels;
    }

    public void setShowTickLabels(boolean showTickLabels) {
        this.showTickLabels = showTickLabels;
    }

    public Orientation getOrientation() {
        return orientation;
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
    }

    public Range<T> getRange() {
        return range;
    }
}
