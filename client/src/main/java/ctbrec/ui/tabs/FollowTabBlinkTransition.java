package ctbrec.ui.tabs;

import javafx.animation.Transition;
import javafx.scene.control.Tab;
import javafx.scene.paint.Color;
import javafx.util.Duration;

public class FollowTabBlinkTransition extends Transition {

    private final String normalStyle;
    private final Tab followedTab;
    private final Color normal;
    private final Color highlight;

    FollowTabBlinkTransition(Tab followedTab) {
        this.followedTab = followedTab;
        normalStyle = followedTab.getStyle();
        normal = Color.web("#f4f4f4");
        highlight = Color.web("#2b8513");

        setCycleDuration(Duration.millis(500));
        setCycleCount(6);
        setAutoReverse(true);
        setOnFinished(evt -> followedTab.setStyle(normalStyle));
    }

    @Override
    protected void interpolate(double fraction) {
        double rh = highlight.getRed();
        double rn = normal.getRed();
        double diff = rh - rn;
        double r = (rn + diff * fraction) * 255;
        double gh = highlight.getGreen();
        double gn = normal.getGreen();
        diff = gh - gn;
        double g = (gn + diff * fraction) * 255;
        double bh = highlight.getBlue();
        double bn = normal.getBlue();
        diff = bh - bn;
        double b = (bn + diff * fraction) * 255;
        String style = "-fx-background-color: rgb(" + r + "," + g + "," + b + ")";
        followedTab.setStyle(style);
    }
}
