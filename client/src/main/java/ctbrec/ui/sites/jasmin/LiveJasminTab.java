package ctbrec.ui.sites.jasmin;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.sites.Site;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.concurrent.WorkerStateEvent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

public class LiveJasminTab extends ThumbOverviewTab {
    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminTab.class);
    protected Label status;
    protected Button acknowledge = new Button("That's alright");
    private boolean betaAcknowledged = Config.getInstance().getSettings().livejasminBetaAcknowledged;

    public LiveJasminTab(String title, PaginatedScheduledService updateService, Site site) {
        super(title, updateService, site);
        if(!betaAcknowledged) {
            status = new Label("LiveJasmin is not fully functional. Live previews do not work.\n"
                    + "If you get errors while loading the tabs, try to create an account and open the Followed tab first.");
            grid.getChildren().add(status);
            grid.getChildren().add(acknowledge);
        } else {
            status = new Label("Loading...");
            grid.getChildren().add(status);
        }

        acknowledge.setOnAction(e -> {
            betaAcknowledged = true;
            Config.getInstance().getSettings().livejasminBetaAcknowledged = true;
            try {
                Config.getInstance().save();
            } catch (IOException e1) {
                LOG.error("Couldn't save config", e1);
            }
            status.setText("Loading...");
            grid.getChildren().remove(acknowledge);
            if(updateService != null) {
                updateService.cancel();
                updateService.reset();
                updateService.restart();
            }
        });
    }

    @Override
    protected void onSuccess() {
        if(Config.getInstance().getSettings().livejasminBetaAcknowledged) {
            grid.getChildren().remove(status);
            grid.getChildren().remove(acknowledge);
            super.onSuccess();
        }
    }

    @Override
    protected void onFail(WorkerStateEvent event) {
        if(Config.getInstance().getSettings().livejasminBetaAcknowledged) {
            status.setText("Error");
            grid.getChildren().remove(acknowledge);
            super.onFail(event);
        }
    }

    public void setScene(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (this.isSelected() && event.getCode() == KeyCode.DELETE) {
                follow(selectedThumbCells, false);
            }
        });
    }
}
