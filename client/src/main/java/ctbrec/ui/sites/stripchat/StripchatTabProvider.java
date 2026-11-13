package ctbrec.ui.sites.stripchat;

import ctbrec.sites.stripchat.Stripchat;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

public class StripchatTabProvider extends AbstractTabProvider {

    private final String urlTemplate;
    private final String urlFilterTemplate;
    private final StripchatFollowedTab followedTab;

    public StripchatTabProvider(Stripchat stripchat) {
        super(stripchat);
        followedTab = new StripchatFollowedTab("Followed", stripchat);
        urlTemplate = stripchat.getBaseUrl() + "/api/front/models?primaryTag={0}&sortBy=viewersRating&withMixedTags=true&parentTag=";
        urlFilterTemplate = stripchat.getBaseUrl() + "/api/front/models?primaryTag=girls&filterGroupTags=%5B%5B%22{0}%22%5D%5D&parentTag={0}";
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls", MessageFormat.format(urlTemplate, "girls")));
        tabs.add(createTab("Girls New", MessageFormat.format(urlFilterTemplate, "autoTagNew")));
        tabs.add(createTab("Girls HD", MessageFormat.format(urlFilterTemplate, "autoTagHd")));
        tabs.add(createTab("Girls VR", MessageFormat.format(urlFilterTemplate, "autoTagVr")));
        tabs.add(createTab("Mobile", MessageFormat.format(urlFilterTemplate, "mobile")));
        tabs.add(createTab("Private", MessageFormat.format(urlFilterTemplate, "autoTagSpy")));
        tabs.add(createTab("Couples", MessageFormat.format(urlTemplate, "couples")));
        tabs.add(createTab("Boys", MessageFormat.format(urlTemplate, "men")));
        tabs.add(createTab("Trans", MessageFormat.format(urlTemplate, "trans")));
        followedTab.setRecorder(recorder);
        followedTab.setScene(scene);
        followedTab.setImageAspectRatio(9.0 / 16.0);
        tabs.add(followedTab);
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private Tab createTab(String title, String url) {
        var updateService = new StripchatUpdateService(url, false, (Stripchat) site);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(recorder);
        tab.setImageAspectRatio(9.0 / 16.0);
        return tab;
    }

}
