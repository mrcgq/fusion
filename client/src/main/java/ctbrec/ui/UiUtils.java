package ctbrec.ui;

import javafx.geometry.Bounds;
import javafx.scene.Node;
import javafx.scene.control.ContextMenu;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class UiUtils {

    private UiUtils() {}

    public static void disableRightClickFor(ContextMenu menu) {
        menu.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> {
            if (event.getButton() == MouseButton.SECONDARY) {
                event.consume();
            }
        });
    }

    public static void ignoreMouseReleasedIfMouseExited(ContextMenu menu) {
        menu.addEventFilter(MouseEvent.MOUSE_RELEASED, evt -> {
            if (evt.getTarget() instanceof Node) {
                Node target = (Node) evt.getTarget();
                Bounds screenBounds = target.localToScreen(target.getBoundsInLocal());
                boolean releasedOnOriginalMouseItem = screenBounds.contains(evt.getScreenX(), evt.getScreenY());
                if (!releasedOnOriginalMouseItem) {
                    evt.consume();
                }
            }
        });
    }
}
