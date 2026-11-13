package ctbrec.ui.sites.jasmin;

import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;

public class LiveJasminTabProvider extends AbstractTabProvider {


    private final LiveJasminFollowedTab followedTab;

    public LiveJasminTabProvider(LiveJasmin site) {
        super(site);
        followedTab = new LiveJasminFollowedTab(site);
        followedTab.setRecorder(recorder);
        followedTab.setImageAspectRatio(9.0 / 16.0);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls", site.getBaseUrl() + "/en/girls/?listPageOrderType=most_popular"));
        tabs.add(createTab("New Girls", site.getBaseUrl() + "/en/girls/new-models/?listPageOrderType=most_popular"));
        tabs.add(createTab("Boys", site.getBaseUrl() + "/en/boys/?listPageOrderType=most_popular"));
        tabs.add(createTab("New Boys", site.getBaseUrl() + "/en/boys/new-models/?listPageOrderType=most_popular"));
        tabs.add(createTab("Couples", site.getBaseUrl() + "/en/girls/couple/?listPageOrderType=most_popular"));
        tabs.add(createTab("Trans", site.getBaseUrl() + "/en/boys/transboy/?listPageOrderType=most_popular"));
        tabs.add(followedTab);
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private ThumbOverviewTab createTab(String title, String url) {
        var s = new LiveJasminUpdateService((LiveJasmin) site, url);
        s.setPeriod(Duration.seconds(60));
        ThumbOverviewTab tab = new ThumbOverviewTab(title, s, site);
        tab.setRecorder(recorder);
        tab.setImageAspectRatio(9.0 / 16.0);
        return tab;
    }
}
