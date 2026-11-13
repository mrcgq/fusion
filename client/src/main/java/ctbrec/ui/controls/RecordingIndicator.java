package ctbrec.ui.controls;

import javafx.scene.Cursor;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;

public class RecordingIndicator extends StackPane {

    private ImageView icon;
    private Rectangle clickPanel;

    public RecordingIndicator(int size) {
        setMaxSize(size, size);

        icon = new ImageView();
        icon.setVisible(false);
        icon.prefHeight(size);
        icon.prefWidth(size);
        icon.maxHeight(size);
        icon.maxWidth(size);
        clickPanel = new Rectangle(size, size);
        clickPanel.setCursor(Cursor.HAND);
        clickPanel.setFill(Paint.valueOf("#00000000"));
        getChildren().add(icon);
        getChildren().add(clickPanel);

        icon.visibleProperty().bindBidirectional(visibleProperty());
    }

    public void setImage(Image img) {
        icon.setImage(img);
    }
}
