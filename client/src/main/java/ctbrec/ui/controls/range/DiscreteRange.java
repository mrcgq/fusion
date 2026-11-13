package ctbrec.ui.controls.range;

import java.util.List;

public class DiscreteRange<T> implements Range<T> {

    private List<T> values;
    private List<?> labels;


    public DiscreteRange(List<T> values, List<?> labels) {
        this.values = values;
        this.labels = labels;
        if (values == null) {
            throw new IllegalArgumentException("Values cannot be null");
        }
        if (labels == null) {
            throw new IllegalArgumentException("Labels cannot be null");
        }

    }

    @Override
    public T getMinimum() {
        return values.get(0);
    }

    @Override
    public T getMaximum() {
        return values.get(values.size()-1);
    }

    @Override
    public List<T> getTicks() {
        return values;
    }

    public List<?> getLabels() {
        return labels;
    }

    @Override
    public String toString() {
        return labels.toString();
    }
}
