package ctbrec.ui.sites.amateurtv;

import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class AmateurTvTabProvider extends AbstractTabProvider {


    private AmateurTvFollowedTab followedTab;

    public AmateurTvTabProvider(AmateurTv amateurTv) {
        super(amateurTv);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();

        // all
        var url = AmateurTv.BASE_URL + "/v3/readmodel/cache/onlinecamlist";
        var updateService = new AmateurTvUpdateService((AmateurTv) site, url);
        tabs.add(createTab("All", updateService));

        // female
        url = AmateurTv.BASE_URL + "/v3/readmodel/cache/sectioncamlist?genre=[%22w%22]";
        updateService = new AmateurTvUpdateService((AmateurTv) site, url);
        tabs.add(createTab("Female", updateService));

        // male
        url = AmateurTv.BASE_URL + "/v3/readmodel/cache/sectioncamlist?genre=[%22m%22]";
        updateService = new AmateurTvUpdateService((AmateurTv) site, url);
        tabs.add(createTab("Male", updateService));

        // couples
        url = AmateurTv.BASE_URL + "/v3/readmodel/cache/sectioncamlist?genre=[%22c%22]";
        updateService = new AmateurTvUpdateService((AmateurTv) site, url);
        tabs.add(createTab("Couples", updateService));

        // trans
        url = AmateurTv.BASE_URL + "/v3/readmodel/cache/sectioncamlist?genre=[%22t%22]";
        updateService = new AmateurTvUpdateService((AmateurTv) site, url);
        tabs.add(createTab("Trans", updateService));

        // followed
        url = AmateurTv.BASE_URL + "/v3/readmodel/cache/favorites";
        updateService = new AmateurTvUpdateService((AmateurTv) site, url);
        updateService.requiresLogin(true);
        followedTab = new AmateurTvFollowedTab("Followed", updateService, site);
        followedTab.setRecorder(recorder);
        tabs.add(followedTab);

        return tabs;
    }

    private Tab createTab(String title, PaginatedScheduledService updateService) {
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(recorder);
        return tab;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

}
