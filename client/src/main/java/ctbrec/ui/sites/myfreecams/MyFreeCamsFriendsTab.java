package ctbrec.ui.sites.myfreecams;
import static ctbrec.ui.sites.myfreecams.FriendsUpdateService.Mode.*;

import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

public class MyFreeCamsFriendsTab extends ThumbOverviewTab implements FollowedTab {
    public MyFreeCamsFriendsTab(MyFreeCams mfc) {
        super("Friends", new FriendsUpdateService(mfc), mfc);
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
            if(online.isSelected()) {
                ((FriendsUpdateService)updateService).setMode(ONLINE);
            } else {
                ((FriendsUpdateService)updateService).setMode(OFFLINE);
            }
            queue.clear();
            updateService.restart();
        });
    }

    public void setScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (this.isSelected() && event.getCode() == KeyCode.DELETE) {
                follow(selectedThumbCells, false);
            }
        });
    }
}
