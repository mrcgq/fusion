package ctbrec.ui.sites.flirt4free;

import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class Flirt4FreeFavoritesTab extends ThumbOverviewTab implements FollowedTab {

    public Flirt4FreeFavoritesTab(Flirt4Free flirt4free) {
        super("Favorites", new Flirt4FreeFavoritesUpdateService(flirt4free), flirt4free);
    }

    public void setScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (this.isSelected() && event.getCode() == KeyCode.DELETE) {
                follow(selectedThumbCells, false);
            }
        });
    }
}
