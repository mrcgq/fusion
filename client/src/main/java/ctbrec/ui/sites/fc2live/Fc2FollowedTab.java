package ctbrec.ui.sites.fc2live;

import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.geometry.Insets;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

public class Fc2FollowedTab extends ThumbOverviewTab implements FollowedTab {

    public Fc2FollowedTab(Fc2Live fc2live) {
        super("Followed", new Fc2FollowedUpdateService(fc2live), fc2live);
    }

    @SuppressWarnings("unused")
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
            ((Fc2FollowedUpdateService)updateService).setShowOnline(online.isSelected());
            queue.clear();
            updateService.restart();
        });
    }
}
