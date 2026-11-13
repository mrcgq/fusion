package ctbrec.ui.controls.range;

import java.util.Collections;
import java.util.List;

import javafx.scene.chart.ValueAxis;

public class LabeledNumberAxis extends ValueAxis<Number> {

    private DiscreteRange<Number> range;

    public LabeledNumberAxis(DiscreteRange<Number> range) {
        this.range = range;
    }

    @Override
    protected List<Number> calculateMinorTickMarks() {
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void setRange(Object range, boolean animate) {
        if (!(range instanceof DiscreteRange)) {
            throw new IllegalArgumentException("Range has to be of type DiscreteRange");
        }
        this.range = (DiscreteRange<Number>) range;
    }

    @Override
    protected Object getRange() {
        return range;
    }

    @Override
    protected List<Number> calculateTickValues(double length, Object range) {
        if (!(range instanceof Range)) {
            throw new IllegalArgumentException("Range has to be of type ctbrec.ui.controls.range.Range<T>");
        }
        @SuppressWarnings("unchecked")
        Range<Number> discreteRange = (Range<Number>) range;
        return discreteRange.getTicks();
    }

    @Override
    protected String getTickMarkLabel(Number value) {
        return range.getLabels().get(value.intValue()).toString();
    }
}
