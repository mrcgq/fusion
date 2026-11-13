package ctbrec.ui.sites.showup;

import ctbrec.sites.showup.Showup;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class ShowupTabProvider extends AbstractTabProvider {

    public ShowupTabProvider(Showup site) {
        super(site);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Women", "female"));
        tabs.add(createTab("Men", "male"));
        tabs.add(createTab("All", "all"));
        var showupFollowedTab = new ShowupFollowedTab("Favorites", (Showup) site);
        showupFollowedTab.setRecorder(site.getRecorder());
        tabs.add(showupFollowedTab);
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return null;
    }

    private Tab createTab(String title, String category) {
        var updateService = new ShowupUpdateService((Showup) site, category);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(site.getRecorder());
        return tab;
    }
}
