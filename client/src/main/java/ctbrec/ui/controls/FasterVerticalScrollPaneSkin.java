package ctbrec.ui.controls;

import javafx.scene.control.ScrollPane;
import javafx.scene.control.skin.ScrollPaneSkin;
import javafx.scene.input.ScrollEvent;

public class FasterVerticalScrollPaneSkin extends ScrollPaneSkin {

    public FasterVerticalScrollPaneSkin(final ScrollPane scrollPane) {
        super(scrollPane);

        getSkinnable().addEventFilter(ScrollEvent.SCROLL, event -> {
            var ratio = scrollPane.getViewportBounds().getHeight() / scrollPane.getContent().getBoundsInLocal().getHeight();
            var baseUnitIncrement = 0.15;
            var unitIncrement = baseUnitIncrement * ratio * 1.25;
            getVerticalScrollBar().setUnitIncrement(unitIncrement);

            if (event.getDeltaX() < 0) {
                getHorizontalScrollBar().increment();
            } else if (event.getDeltaX() > 0) {
                getHorizontalScrollBar().decrement();
            }
            if (event.getDeltaY() < 0) {
                getVerticalScrollBar().increment();
            } else if (event.getDeltaY() > 0) {
                getVerticalScrollBar().decrement();
            }
            event.consume();
        });
    }
}
