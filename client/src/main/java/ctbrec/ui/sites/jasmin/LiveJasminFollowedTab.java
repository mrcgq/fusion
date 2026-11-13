package ctbrec.ui.sites.jasmin;

import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.ui.tabs.FollowedTab;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

public class LiveJasminFollowedTab extends LiveJasminTab implements FollowedTab {

    public LiveJasminFollowedTab(LiveJasmin liveJasmin) {
        super("Followed", new LiveJasminFollowedUpdateService(liveJasmin), liveJasmin);
    }

    @Override
    public void setScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (this.isSelected() && event.getCode() == KeyCode.DELETE) {
                follow(selectedThumbCells, false);
            }
        });
    }

    @Override
    protected void createGui() {
        super.createGui();
        addOnlineOfflineSelector();
    }

    private void addOnlineOfflineSelector() {
        var group = new ToggleGroup();
        var online = new RadioButton("online");
        online.setToggleGroup(group);
        var offline = new RadioButton("offline");
        offline.setToggleGroup(group);
        pagination.getChildren().add(online);
        pagination.getChildren().add(offline);
        HBox.setMargin(online, new Insets(5,5,5,40));
        HBox.setMargin(offline, new Insets(5,5,5,5));
        online.setSelected(true);
        group.selectedToggleProperty().addListener(e -> {
            ((LiveJasminFollowedUpdateService)updateService).setShowOnline(online.isSelected());
            queue.clear();
            updateService.restart();
        });
    }
}
