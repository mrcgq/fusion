
package ctbrec.ui.sites.cherrytv;

import ctbrec.sites.cherrytv.CherryTv;
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

public class CherryTvFollowedTab extends ThumbOverviewTab implements FollowedTab {
    private final Label status;
    private ToggleGroup group;

    public CherryTvFollowedTab(String title, CherryTv site) {
        super(title, new CherryTvFollowedUpdateService(site), site);
        status = new Label("Logging in...");
        grid.getChildren().add(status);
    }

    @Override
    protected void createGui() {
        super.createGui();
        group = new ToggleGroup();
        addOnlineOfflineSelector();
        setFilter(true);
    }

    private void addOnlineOfflineSelector() {
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
            setFilter(online.isSelected());
            queue.clear();
            updateService.restart();
        });
    }

    private void setFilter(boolean online) {
        ((CherryTvUpdateService) updateService).setFilter(m -> {
            try {
                return m.isOnline(false) == online;
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception ex) {
                return false;
            }
        });
    }

    @Override
    protected void onSuccess() {
        grid.getChildren().remove(status);
        super.onSuccess();
    }

    @Override
    protected void onFail(WorkerStateEvent event) {
        status.setText("Login failed");
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
