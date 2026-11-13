package ctbrec.ui.tabs.recorded;

import javafx.geometry.Insets;
import javafx.scene.control.TableColumn;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;

public class ImageTableCell extends ClickableTableCell<Image> {

    protected ImageView imageView;

    public ImageTableCell() {
        imageView = new ImageView();
        imageView.setSmooth(true);
        imageView.setPreserveRatio(true);
        imageView.prefHeight(64);
        imageView.setFitHeight(64);

        setPadding(new Insets(5));
        setGraphic(imageView);
    }

    @Override
    public void requestLayout() {
        TableColumn<?, ?> tc = getTableColumn();
        if (tc != null) {
            double columnWidth = getTableColumn().getWidth();
            Insets pd = getPadding();
            var height = columnWidth - pd.getTop() - pd.getBottom();
            var width = columnWidth - pd.getLeft() - pd.getRight();
            imageView.prefHeight(height);
            imageView.setFitHeight(height);
            imageView.prefWidth(width);
            imageView.setFitWidth(width);

            var clip = new Rectangle(width, height);
            clip.setArcHeight(10);
            clip.setArcWidth(10);
            imageView.setClip(clip);
        }
        super.requestLayout();
    }

    @Override
    protected void updateItem(Image image, boolean empty) {
        imageView.setImage(empty ? null : image);
        super.requestLayout();
    }
}
