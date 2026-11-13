package ctbrec.ui.tabs;

import static ctbrec.ui.controls.Dialogs.*;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Model;
import ctbrec.sites.Site;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.controls.SearchPopover;
import ctbrec.ui.controls.SearchPopoverTreeList;
import javafx.application.Platform;
import javafx.concurrent.Task;

public class ThumbOverviewTabSearchTask extends Task<List<Model>> {

    private static final Logger LOG = LoggerFactory.getLogger(ThumbOverviewTabSearchTask.class);

    private final Site site;
    private final SearchPopover popover;
    private final SearchPopoverTreeList popoverTreeList;
    private final String query;

    public ThumbOverviewTabSearchTask(Site site, SearchPopover popover, SearchPopoverTreeList popoverTreeList, String query) {
        this.site = site;
        this.popover = popover;
        this.popoverTreeList = popoverTreeList;
        this.query = query;
    }

    @Override
    protected List<Model> call() throws Exception {
        if(site.searchRequiresLogin()) {
            var loggedin = false;
            try {
                loggedin = SiteUiFactory.getUi(site).login();
            } catch (IOException e) {
                // nothing to do
            }
            if(!loggedin) {
                showError(popover.getScene(), "Login failed", "Search won't work correctly without login", null);
            }
        }
        return site.search(query);
    }

    @Override
    protected void failed() {
        LOG.error("Search failed", getException());
    }

    @Override
    protected void succeeded() {
        Platform.runLater(() -> {
            List<Model> models = getValue();
            LOG.debug("Search result {} {}", isCancelled(), models);
            if(models.isEmpty()) {
                popover.hide();
            } else {
                popoverTreeList.getItems().clear();
                for (Model model : getValue()) {
                    popoverTreeList.getItems().add(model);
                }
                popover.show();
            }
        });
    }
}
