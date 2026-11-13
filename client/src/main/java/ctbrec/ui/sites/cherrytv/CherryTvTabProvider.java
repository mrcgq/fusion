package ctbrec.ui.sites.cherrytv;

import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class CherryTvTabProvider extends AbstractTabProvider {

    private final CherryTvFollowedTab followedTab;

    public CherryTvTabProvider(CherryTv cherryTv) {
        super(cherryTv);

        followedTab = new CherryTvFollowedTab("Following", (CherryTv) site);
        followedTab.setImageAspectRatio(1);
        followedTab.preserveAspectRatioProperty().set(false);
        followedTab.setRecorder(recorder);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();

        tabs.add(createTab("Female", "girls"));
        tabs.add(createTab("Trans", "trans"));
        tabs.add(createTab("Group Show", "groupshow"));
        tabs.add(followedTab);
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private Tab createTab(String name, String url) {
        var updateService = new CherryTvUpdateService(url, (CherryTv) site, false);
        var tab = new ThumbOverviewTab(name, updateService, site);
        tab.setImageAspectRatio(9.0 / 16.0);
        tab.preserveAspectRatioProperty().set(false);
        tab.setRecorder(recorder);
        return tab;
    }

}
