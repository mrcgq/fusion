package ctbrec.ui.sites.showup;

import ctbrec.sites.showup.Showup;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

public class ShowupFollowedTab extends ThumbOverviewTab implements FollowedTab {
    private Label status;
    boolean showOnline = true;

    public ShowupFollowedTab(String title, Showup showup) {
        super(title, new ShowupFollowedUpdateService(showup), showup);
        status = new Label("Logging in...");
        grid.getChildren().add(status);
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
            queue.clear();
            ((ShowupFollowedUpdateService)updateService).showOnline(online.isSelected());
            updateService.restart();
        });
    }

    @Override
    protected void onSuccess() {
        grid.getChildren().remove(status);
        super.onSuccess();
    }

    @Override
    protected void onFail(WorkerStateEvent event) {
        var msg = "";
        if (event.getSource().getException() != null) {
            msg = ": " + event.getSource().getException().getMessage();
        }
        status.setText("Login failed" + msg);
        super.onFail(event);
    }

    @Override
    public void selected() {
        status.setText("Logging in...");
        super.selected();
    }

    public void setScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (this.isSelected() && event.getCode() == KeyCode.DELETE) {
                follow(selectedThumbCells, false);
            }
        });
    }
}
