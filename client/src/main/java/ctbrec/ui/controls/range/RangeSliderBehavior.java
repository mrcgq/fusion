package ctbrec.ui.controls.range;

import java.util.List;

import com.sun.javafx.scene.control.behavior.BehaviorBase; // NOSONAR
import com.sun.javafx.scene.control.inputmap.InputMap; // NOSONAR

import javafx.scene.Node;
import javafx.scene.input.MouseEvent;

public class RangeSliderBehavior<T extends Number> extends BehaviorBase<RangeSlider<T>> {

    private RangeSlider<T> rangeSlider;

    public RangeSliderBehavior(RangeSlider<T> rangeSlider) {
        super(rangeSlider);
        this.rangeSlider = rangeSlider;
        rangeSlider.addEventFilter(MouseEvent.MOUSE_CLICKED, this::sliderClicked);
    }

    private void sliderClicked(MouseEvent me) {
        Node source = (Node) me.getSource();
        double positionPercentage = me.getX() / source.getBoundsInParent().getWidth();
        moveClosestThumbTo(positionPercentage);
    }

    @Override
    public InputMap<RangeSlider<T>> getInputMap() {
        InputMap<RangeSlider<T>> inputMap = new InputMap<>(rangeSlider);
        return inputMap;
    }

    /**
     * @param position
     *            The mouse position on track with 0.0 being beginning of track and 1.0 being the end
     */
    public void lowThumbDragged(double position) {
        var newPosition = getNewPosition(position);
        var high = getHigh();
        if (newPosition.doubleValue() >= high.doubleValue()) {
            newPosition = getLow();
        }
        rangeSlider.setLow(newPosition);
    }

    /**
     * @param position
     *            The mouse position on track with 0.0 being beginning of track and 1.0 being the end
     */
    public void highThumbDragged(double position) {
        var newPosition = getNewPosition(position);
        var low = getLow();
        if (newPosition.doubleValue() <= low.doubleValue()) {
            newPosition = getHigh();
        }
        rangeSlider.setHigh(newPosition);
    }

    public void moveClosestThumbTo(double positionPercentage) {
        var newPosition = getNewPosition(positionPercentage);
        var low = getLow();
        var high = getHigh();
        double distToLow = Math.abs(low.doubleValue() - newPosition.doubleValue());
        double distToHigh = Math.abs(high.doubleValue() - newPosition.doubleValue());
        if (distToLow < distToHigh) {
            rangeSlider.setLow(newPosition);
        } else {
            rangeSlider.setHigh(newPosition);
        }
    }

    private T getLow() {
        return rangeSlider.getRange().getTicks().get(rangeSlider.getLow().get().intValue());
    }

    private T getHigh() {
        return rangeSlider.getRange().getTicks().get(rangeSlider.getHigh().get().intValue());
    }

    /**
     * Calculate the new position of the thumb given the clicked/dragged position
     *
     * @param position
     *            clicked position
     * @return new position
     */
    private T getNewPosition(double position) {
        List<T> ticks = rangeSlider.getRange().getTicks();
        double percentPerTick = 1d / (ticks.size() - 1);
        int index = (int) Math.round(position / percentPerTick);
        index = Math.min(ticks.size() - 1, Math.max(0, index));
        return ticks.get(index);
    }

}
