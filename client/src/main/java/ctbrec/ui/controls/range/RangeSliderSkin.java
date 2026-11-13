package ctbrec.ui.controls.range;

import javafx.geometry.Orientation;
import javafx.geometry.Point2D;
import javafx.geometry.Side;
import javafx.scene.chart.Axis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.ValueAxis;
import javafx.scene.control.SkinBase;
import javafx.scene.layout.StackPane;

public class RangeSliderSkin extends SkinBase<RangeSlider<?>> {

    private static final double TRACK_TO_TICK_GAP = 2;
    private Axis<?> tickLine = null;
    private StackPane track;
    private ThumbRange thumbRange = new ThumbRange();
    private RangeSliderBehavior<?> behavior;
    private double thumbWidth;
    private double thumbHeight;
    private boolean showTickMarks;
    private double trackStart;
    private double trackLength;
    private double lowThumbPos;

    private double preDragPos;          // used as a temp value for low and high thumbsRange
    private Point2D preDragThumbPoint;  // in skin coordinates

    protected RangeSliderSkin(RangeSlider<?> control, RangeSliderBehavior<?> behavior) {
        super(control);
        this.behavior = behavior;
        initTrack();
        initThumbs(thumbRange);
        registerChangeListener(control.getLow(), obsVal -> getSkinnable().requestLayout());
        registerChangeListener(control.getHigh(), obsVal -> getSkinnable().requestLayout());
    }

    private void initThumbs(ThumbRange t) {
        setShowTickMarks(getSkinnable().isShowTickMarks(), getSkinnable().isShowTickLabels());

        getChildren().addAll(t.low, t.high, t.rangeBar);

        t.low.setOnMousePressed(me -> {
            preDragThumbPoint = t.low.localToParent(me.getX(), me.getY());
            preDragPos = (getSkinnable().getLow().get().doubleValue() - getSkinnable().getMinimum().doubleValue()) / (getMaxMinusMinNoZero());
        });
        t.low.setOnMouseDragged(me -> {
            Point2D cur = t.low.localToParent(me.getX(), me.getY());
            double dragPos = (isHorizontal()) ? cur.getX() - preDragThumbPoint.getX() : -(cur.getY() - preDragThumbPoint.getY());
            behavior.lowThumbDragged(preDragPos + dragPos / trackLength);
        });

        t.high.setOnMousePressed(me -> {
            preDragThumbPoint = t.high.localToParent(me.getX(), me.getY());
            preDragPos = (getSkinnable().getHigh().get().doubleValue() - getSkinnable().getMinimum().doubleValue()) / (getMaxMinusMinNoZero());
        });
        t.high.setOnMouseDragged(me -> {
            boolean orientation = getSkinnable().getOrientation() == Orientation.HORIZONTAL;
            double trackLen = orientation ? track.getWidth() : track.getHeight();
            Point2D cur = t.high.localToParent(me.getX(), me.getY());
            double dragPos = getSkinnable().getOrientation() != Orientation.HORIZONTAL ? -(cur.getY() - preDragThumbPoint.getY()) : cur.getX() - preDragThumbPoint.getX();
            behavior.highThumbDragged(preDragPos + dragPos / trackLen);
        });
    }

    private boolean isHorizontal() {
        return getSkinnable().getOrientation() == Orientation.HORIZONTAL;
    }

    private void initTrack() {
        track = new StackPane();
        track.getStyleClass().setAll("track");

        getChildren().clear();
        getChildren().add(track);
    }

    private void setShowTickMarks(boolean ticksVisible, boolean labelsVisible) {
        showTickMarks = (ticksVisible || labelsVisible);
        var rangeSlider = getSkinnable();
        if (showTickMarks) {
            if (tickLine == null) {
                var range = rangeSlider.getRange();
                tickLine = createAxis(range, ticksVisible, labelsVisible);
                getChildren().addAll(tickLine);
            } else {
                tickLine.setTickLabelsVisible(labelsVisible);
                tickLine.setTickMarkVisible(ticksVisible);
            }
        }

        getSkinnable().requestLayout();
    }

    @SuppressWarnings("unchecked")
    private Axis<?> createAxis(Range<? extends Number> range, boolean ticksVisible, boolean labelsVisible) {
        ValueAxis<Number> axis;
        if (range instanceof DiscreteRange) {
            axis = new LabeledNumberAxis((DiscreteRange<Number>) range);
        } else {
            axis = new NumberAxis();
        }

        axis.setUpperBound(range.getMaximum().doubleValue());
        axis.setLowerBound(range.getMinimum().doubleValue());
        axis.setMinorTickVisible(false);
        axis.setMinorTickCount(0);
        axis.setAutoRanging(false);
        axis.setAnimated(false);
        axis.setSide(isHorizontal() ? Side.BOTTOM : Side.RIGHT);
        axis.setTickMarkVisible(ticksVisible);
        axis.setTickLabelsVisible(labelsVisible);
        return axis;
    }

    @Override
    protected void layoutChildren(final double x, final double y, final double w, final double h) {
        if (thumbRange != null) {
            thumbWidth = thumbRange.low.prefWidth(-1);
            thumbHeight = thumbRange.low.prefHeight(-1);
            thumbRange.low.resize(thumbWidth, thumbHeight);
            thumbRange.high.resize(thumbWidth, thumbHeight);
        }


        double radius = track.getBackground().getFills().isEmpty() ? 0 : track.getBackground().getFills().get(0).getRadii().getTopLeftHorizontalRadius();
        double trackRadius = track.getBackground() == null ? 0 : radius;

        double tickLineHeight = (showTickMarks) ? tickLine.prefHeight(-1) : 0;
        double trackHeight = 5;
        double trackAreaHeight = Math.max(trackHeight, thumbHeight);
        double totalHeightNeeded = trackAreaHeight + ((showTickMarks) ? TRACK_TO_TICK_GAP + tickLineHeight : 0);
        double startY = y + ((h - totalHeightNeeded) / 2); // center slider in available height vertically

        trackLength = w - thumbWidth;
        trackStart = x + (thumbWidth / 2);

        double trackTop = (int) (startY + ((trackAreaHeight - trackHeight) / 2));
        lowThumbPos = (int) (startY + ((trackAreaHeight - thumbHeight) / 2));

        // layout track
        track.resizeRelocate(trackStart - trackRadius, trackTop, trackLength + trackRadius + trackRadius, trackHeight);

        positionThumbs();

        if (showTickMarks) {
            tickLine.setLayoutX(trackStart);
            tickLine.setLayoutY(trackTop + trackHeight + TRACK_TO_TICK_GAP);
            tickLine.resize(trackLength, tickLineHeight);
            tickLine.requestAxisLayout();
        } else {
            if (tickLine != null) {
                tickLine.resize(0, 0);
                tickLine.requestAxisLayout();
            }
            tickLine = null;
        }
    }

    private void positionThumbs() {
        RangeSlider<?> s = getSkinnable();

        double lxl = trackStart + (trackLength * ((s.getLow().get().doubleValue() - s.getMinimum().doubleValue()) / (getMaxMinusMinNoZero())) - thumbWidth / 2D);
        double lxh = trackStart + (trackLength * ((s.getHigh().get().doubleValue() - s.getMinimum().doubleValue()) / (getMaxMinusMinNoZero())) - thumbWidth / 2D);
        double ly = lowThumbPos;

        if (thumbRange != null) {
            thumbRange.low.setLayoutX(lxl);
            thumbRange.low.setLayoutY(ly);

            thumbRange.high.setLayoutX(lxh);
            thumbRange.high.setLayoutY(ly);

            thumbRange.rangeBar.resizeRelocate(thumbRange.low.getLayoutX() + thumbRange.low.getWidth(), track.getLayoutY(),
                    thumbRange.high.getLayoutX() - thumbRange.low.getLayoutX() - thumbRange.low.getWidth(), track.getHeight());
        }
    }

    private double minTrackLength() {
        return 2 * ((thumbRange != null) ? thumbRange.low.prefWidth(-1) : 1);
    }

    @Override
    protected double computeMinWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (isHorizontal()) {
            return (leftInset + minTrackLength() + ((thumbRange != null) ? thumbRange.low.prefWidth(-1) : 1) + rightInset);
        } else {
            return (leftInset + ((thumbRange != null) ? thumbRange.low.prefWidth(-1) : 1) + rightInset);
        }
    }

    @Override
    protected double computeMinHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (isHorizontal()) {
            return (topInset + ((thumbRange != null) ? thumbRange.low.prefHeight(-1) : 1) + bottomInset);
        } else {
            return (topInset + minTrackLength() + ((thumbRange != null) ? thumbRange.low.prefHeight(-1) : 1) + bottomInset);
        }
    }

    @Override
    protected double computePrefWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (isHorizontal()) {
            if (showTickMarks) {
                double w = Math.max(140, tickLine.prefWidth(-1));
                return w;
            } else {
                return 140;
            }
        } else {
            return leftInset + Math.max(((thumbRange != null) ? thumbRange.low.prefWidth(-1) : 1), track.prefWidth(-1))
            + ((showTickMarks) ? (TRACK_TO_TICK_GAP + tickLine.prefWidth(-1)) : 0) + rightInset;
        }
    }

    @Override
    protected double computePrefHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (isHorizontal()) {
            return getSkinnable().getInsets().getTop() + Math.max(((thumbRange != null) ? thumbRange.low.prefHeight(-1) : 1), track.prefHeight(-1))
            + ((showTickMarks) ? (TRACK_TO_TICK_GAP + tickLine.prefHeight(-1)) : 0) + bottomInset;
        } else {
            if (showTickMarks) {
                return Math.max(140, tickLine.prefHeight(-1));
            } else {
                return 140;
            }
        }
    }

    @Override
    protected double computeMaxWidth(double height, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (isHorizontal()) {
            return Double.MAX_VALUE;
        } else {
            return getSkinnable().prefWidth(-1);
        }
    }

    @Override
    protected double computeMaxHeight(double width, double topInset, double rightInset, double bottomInset, double leftInset) {
        if (isHorizontal()) {
            return getSkinnable().prefHeight(width);
        } else {
            return Double.MAX_VALUE;
        }
    }

    private double getMaxMinusMinNoZero() {
        RangeSlider<?> s = getSkinnable();
        return s.getMaximum().doubleValue() - s.getMinimum().doubleValue() == 0 ? 1 : s.getMaximum().doubleValue() - s.getMinimum().doubleValue();
    }

    private static class ThumbPane extends StackPane {
    }

    private static class ThumbRange {
        ThumbPane low;
        ThumbPane high;
        StackPane rangeBar;

        ThumbRange() {
            low = new ThumbPane();
            low.getStyleClass().setAll("low-thumb");
            low.setFocusTraversable(false);

            high = new ThumbPane();
            high.getStyleClass().setAll("high-thumb");
            high.setFocusTraversable(false);

            rangeBar = new StackPane();
            rangeBar.getStyleClass().setAll("range-bar");
            rangeBar.setFocusTraversable(false);
        }
    }
}
