package ctbrec.ui.controls;

import ctbrec.ui.UiUtils;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;

public class CustomMouseBehaviorContextMenu extends ContextMenu {

    public CustomMouseBehaviorContextMenu(MenuItem...items) {
        super(items);
        UiUtils.disableRightClickFor(this);
        UiUtils.ignoreMouseReleasedIfMouseExited(this);
        setAutoHide(true);
        setHideOnEscape(true);
        setAutoFix(true);
    }
}
