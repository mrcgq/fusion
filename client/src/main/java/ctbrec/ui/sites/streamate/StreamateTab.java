package ctbrec.ui.sites.streamate;

import ctbrec.sites.Site;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.geometry.Insets;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;

public class StreamateTab extends ThumbOverviewTab {

    public StreamateTab(String title, StreamateUpdateService updateService, Site site) {
        super(title, updateService, site);
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
        HBox.setMargin(online, new Insets(5, 5, 5, 40));
        HBox.setMargin(offline, new Insets(5, 5, 5, 5));
        online.setSelected(true);
        group.selectedToggleProperty().addListener(e -> {
            ((StreamateUpdateService) updateService).setOnline(online.isSelected());
            queue.clear();
            updateService.restart();
        });
    }
}
