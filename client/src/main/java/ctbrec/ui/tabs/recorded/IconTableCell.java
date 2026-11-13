package ctbrec.ui.tabs.recorded;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import ctbrec.ui.Icon;
import javafx.geometry.Pos;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

public class IconTableCell<T> extends ClickableTableCell<T> {

    protected String tooltip;
    protected HBox iconRow;
    private Map<Icon, ImageView> icons;

    public IconTableCell(Map<Icon, ImageView> icons) {
        this.icons = Objects.requireNonNullElse(icons, new HashMap<>());
        iconRow = new HBox(3);
        iconRow.setAlignment(Pos.CENTER);
    }

    protected void show(Icon iconName) {
        var imageView = icons.get(iconName);
        if (imageView != null) {
            iconRow.getChildren().remove(imageView);
            iconRow.getChildren().add(imageView);
        }
    }

    protected void hide(Icon iconName) {
        var imageView = icons.get(iconName);
        if (imageView != null) {
            iconRow.getChildren().remove(imageView);
        }
    }

    @Override
    protected void updateItem(T value, boolean empty) {
        if (tooltip != null) {
            setTooltip(new Tooltip(tooltip));
        } else {
            setTooltip(null);
        }
        setGraphic(iconRow);
    }
}
