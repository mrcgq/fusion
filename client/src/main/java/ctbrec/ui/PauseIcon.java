package ctbrec.ui;


import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;

public class PauseIcon extends Polygon {

    public PauseIcon(Color color, int size) {
        super(
                0, size,
                0, 0,
                (size * 2.0 / 5.0), 0,
                (size * 2.0 / 5.0), size,
                (size * 3.0 / 5.0), size,
                (size * 3.0 / 5.0), 0,
                size, 0,
                size, size
                );
        setFill(color);
    }
}
