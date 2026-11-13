package ctbrec.ui.sites.myfreecams;

import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class MyFreeCamsTabProvider extends AbstractTabProvider {
    private MyFreeCamsFriendsTab friends;


    public MyFreeCamsTabProvider(MyFreeCams myFreeCams) {
        super(myFreeCams);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();

        PaginatedScheduledService updateService = new OnlineCamsUpdateService();
        tabs.add(createTab("Online", updateService));

        friends = new MyFreeCamsFriendsTab((MyFreeCams) site);
        friends.setRecorder(recorder);
        friends.setImageAspectRatio(9.0 / 16.0);
        friends.preserveAspectRatioProperty().set(false);
        tabs.add(friends);

        updateService = new HDCamsUpdateService();
        tabs.add(createTab("HD", updateService));

        updateService = new PopularModelService();
        tabs.add(createTab("Most Popular", updateService));

        updateService = new NewModelService();
        tabs.add(createTab("New", updateService));

        tabs.add(new MyFreeCamsTableTab((MyFreeCams) site, recorder));

        return tabs;
    }

    private ThumbOverviewTab createTab(String title, PaginatedScheduledService updateService) {
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setImageAspectRatio(9.0 / 16.0);
        tab.preserveAspectRatioProperty().set(false);
        tab.setRecorder(recorder);
        return tab;
    }

    @Override
    public Tab getFollowedTab() {
        return friends;
    }
}
