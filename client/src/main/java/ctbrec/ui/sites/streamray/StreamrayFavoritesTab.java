package ctbrec.ui.sites.streamray;

import ctbrec.sites.streamray.Streamray;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.concurrent.WorkerStateEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;

public class StreamrayFavoritesTab extends ThumbOverviewTab implements FollowedTab {
    private final Label status;
    private final Button loginButton;
    private final StreamrayFavoritesService streamrayFavoritesService;

    public StreamrayFavoritesTab(String title, StreamrayFavoritesService updateService, Streamray site) {
        super(title, updateService, site);
        this.streamrayFavoritesService = updateService;

        status = new Label("Logging in...");
        grid.getChildren().addAll(status);

        loginButton = new Button("Login");
        loginButton.setPadding(new Insets(20));
        loginButton.setOnAction(e -> {
            try {
                new StreamrayElectronLoginDialog(site.getHttpClient().getCookieJar());
                queue.clear();
                updateService.restart();
            } catch (Exception ex) {
                // fail silently
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
        HBox.setMargin(online, new Insets(5, 5, 5, 40));
        HBox.setMargin(offline, new Insets(5, 5, 5, 5));
        online.setSelected(true);
        group.selectedToggleProperty().addListener(e -> {
            ((StreamrayFavoritesService) updateService).setOnline(online.isSelected());
            queue.clear();
            updateService.restart();
        });
    }

    protected void addLoginButton() {
        grid.getChildren().clear();
        grid.setAlignment(Pos.CENTER);
        grid.getChildren().add(loginButton);
    }

    @Override
    protected void onSuccess() {
        grid.getChildren().removeAll(status, loginButton);
        grid.setAlignment(Pos.TOP_LEFT);
        if (!streamrayFavoritesService.isLoggedIn()) {
            addLoginButton();
        } else {
            super.onSuccess();
        }
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
