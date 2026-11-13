package ctbrec.ui.sites.chaturbate;

import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class ChaturbateTabProvider extends AbstractTabProvider {

    private final String apiUrl;
    private final ChaturbateFollowedTab followedTab;

    public ChaturbateTabProvider(Chaturbate chaturbate) {
        super(chaturbate);
        apiUrl = site.getBaseUrl() + "/api/ts";
        this.followedTab = new ChaturbateFollowedTab("Followed", apiUrl + "/roomlist/room-list/?enable_recommendations=false&follow=true", chaturbate);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Featured", apiUrl + "/roomlist/room-list/?enable_recommendations=false"));
        tabs.add(createTab("Female", apiUrl + "/roomlist/room-list/?enable_recommendations=false&genders=f"));
        tabs.add(createTab("New Female", apiUrl + "/roomlist/room-list/?enable_recommendations=false&genders=f&new_cams=true"));
        tabs.add(createTab("Male", apiUrl + "/roomlist/room-list/?enable_recommendations=false&genders=m"));
        tabs.add(createTab("New Male", apiUrl + "/roomlist/room-list/?enable_recommendations=false&genders=m&new_cams=true"));
        tabs.add(createTab("Couples", apiUrl + "/roomlist/room-list/?enable_recommendations=false&genders=c"));
        tabs.add(createTab("Trans", apiUrl + "/roomlist/room-list/?enable_recommendations=false&genders=t"));
        tabs.add(createTab("Private", apiUrl + "/roomlist/room-list/?enable_recommendations=false&private=true"));
        tabs.add(createTab("Hidden", apiUrl + "/roomlist/room-list/?enable_recommendations=false&hidden=true"));
        tabs.add(createTab("Gaming", apiUrl + "/roomlist/room-list/?enable_recommendations=false&gaming=true"));
        followedTab.setScene(scene);
        followedTab.setRecorder(recorder);
        followedTab.setImageAspectRatio(9.0 / 16.0);
        tabs.add(followedTab);
        tabs.add(createApiTab("Top Rated", apiUrl + "/discover/carousels/top-rated/"));
        tabs.add(createApiTab("Trending", apiUrl + "/discover/carousels/trending/"));
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private Tab createTab(String title, String url) {
        var updateService = new ChaturbateUpdateService(url, false, (Chaturbate) site);
        return createTab(title, updateService);
    }

    private Tab createTab(String title, PaginatedScheduledService updateService) {
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(recorder);
        tab.setImageAspectRatio(9.0 / 16.0);
        return tab;
    }

    private Tab createApiTab(String title, String apiUrl) {
        var updateService = new ChaturbateApiUpdateService(apiUrl, (Chaturbate) site);
        return createTab(title, updateService);
    }
}
