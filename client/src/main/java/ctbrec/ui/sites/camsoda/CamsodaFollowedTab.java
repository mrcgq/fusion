package ctbrec.ui.sites.camsoda;

import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.camsoda.CamsodaModel;
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

import java.util.function.Predicate;

public class CamsodaFollowedTab extends ThumbOverviewTab implements FollowedTab {
    private final Label status;
    boolean showOnline = true;

    public CamsodaFollowedTab(String title, Camsoda camsoda) {
        super(title, new CamsodaFollowedUpdateService(camsoda.getBaseUrl() + "/api/v1/browse/react/followed", true, camsoda, m -> true), camsoda);
        status = new Label("Logging in...");
        grid.getChildren().add(status);
        ((CamsodaUpdateService) updateService).setFilter(createFilter(this));
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
            showOnline = online.isSelected();
            queue.clear();
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

    private static Predicate<CamsodaModel> createFilter(CamsodaFollowedTab tab) {
        return m -> {
            try {
                return m.isOnline() == tab.showOnline;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            } catch (Exception e) {
                return false;
            }
        };

    }
}
