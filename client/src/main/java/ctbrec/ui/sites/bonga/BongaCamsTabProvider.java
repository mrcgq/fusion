package ctbrec.ui.sites.bonga;

import ctbrec.sites.bonga.BongaCams;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class BongaCamsTabProvider extends AbstractTabProvider {

    private BongaCamsFriendsTab friendsTab;

    public BongaCamsTabProvider(BongaCams bongaCams) {
        super(bongaCams);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();

        // female
        String url = site.getBaseUrl() + "/tools/listing_v3.php?livetab=female&online_only=true&is_mobile=true&limit=72&offset=";
        var updateService = new BongaCamsUpdateService((BongaCams) site, url);
        tabs.add(createTab("Female", updateService));

        // male
        url = site.getBaseUrl() + "/tools/listing_v3.php?livetab=male&online_only=true&is_mobile=true&limit=72&offset=";
        updateService = new BongaCamsUpdateService((BongaCams) site, url);
        tabs.add(createTab("Male", updateService));

        // couples
        url = site.getBaseUrl() + "/tools/listing_v3.php?livetab=couples&online_only=true&is_mobile=true&limit=72&offset=";
        updateService = new BongaCamsUpdateService((BongaCams) site, url);
        tabs.add(createTab("Couples", updateService));

        // trans
        url = site.getBaseUrl() + "/tools/listing_v3.php?livetab=transsexual&online_only=true&is_mobile=true&limit=72&offset=";
        updateService = new BongaCamsUpdateService((BongaCams) site, url);
        tabs.add(createTab("Transsexual", updateService));

        // new
        url = site.getBaseUrl() + "/tools/listing_v3.php?livetab=new&online_only=true&is_mobile=true&limit=72&offset=";
        updateService = new BongaCamsUpdateService((BongaCams) site, url);
        tabs.add(createTab("New", updateService));

        // friends
        url = site.getBaseUrl() + "/tools/listing_v3.php?livetab=friends&online_only=true&limit=72&offset=";
        updateService = new BongaCamsUpdateService((BongaCams) site, url, true);
        friendsTab = new BongaCamsFriendsTab("Friends", updateService, site);
        friendsTab.setRecorder(recorder);
        tabs.add(friendsTab);

        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return friendsTab;
    }

    private Tab createTab(String title, PaginatedScheduledService updateService) {
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(recorder);
        return tab;
    }

}
