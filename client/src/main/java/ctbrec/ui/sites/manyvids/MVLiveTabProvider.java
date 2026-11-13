package ctbrec.ui.sites.manyvids;

import ctbrec.sites.manyvids.MVLive;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class MVLiveTabProvider extends AbstractTabProvider {

    public MVLiveTabProvider(MVLive mvlive) {
        super(mvlive);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Online"));
        return tabs;
    }

    private Tab createTab(String title) {
        var updateService = new MVLiveUpdateService((MVLive) site, "https://api.vidchat.manyvids.com/creator/live");
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(site.getRecorder());
        tab.setImageAspectRatio(1);
        return tab;
    }
}
