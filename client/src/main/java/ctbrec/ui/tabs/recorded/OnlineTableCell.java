package ctbrec.ui.tabs.recorded;

import static ctbrec.ui.Icon.*;

import java.util.Map;

import com.google.common.base.Objects;

import javafx.scene.image.ImageView;

public class OnlineTableCell extends IconTableCell<Boolean> {

    public OnlineTableCell() {
        super(Map.of(CHECK_16, new ImageView(CHECK_16.url())));
    }

    @Override
    protected void updateItem(Boolean value, boolean empty) {
        hide(CHECK_16);
        tooltip = null;
        if (!empty && Objects.equal(value, Boolean.TRUE)) {
            show(CHECK_16);
            tooltip = "Online";
        }
        super.updateItem(value, empty);
    }
}
