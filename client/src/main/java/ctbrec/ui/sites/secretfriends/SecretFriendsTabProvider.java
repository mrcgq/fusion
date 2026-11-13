package ctbrec.ui.sites.secretfriends;

import ctbrec.sites.secretfriends.SecretFriends;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class SecretFriendsTabProvider extends AbstractTabProvider {

    public SecretFriendsTabProvider(SecretFriends site) {
        super(site);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls", SecretFriends.BASE_URI + "/users"));
        tabs.add(createTab("New", SecretFriends.BASE_URI + "/newgirls"));
        tabs.add(createTab("Couples", SecretFriends.BASE_URI + "/site/couple"));
        return tabs;
    }

    private Tab createTab(String title, String url) {
        var updateService = new SecretFriendsUpdateService(url, false, (SecretFriends) site);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(recorder);
        return tab;
    }

}
