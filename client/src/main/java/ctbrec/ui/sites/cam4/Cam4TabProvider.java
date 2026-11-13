package ctbrec.ui.sites.cam4;

import ctbrec.sites.cam4.Cam4;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class Cam4TabProvider extends AbstractTabProvider {

    private Cam4FollowedTab followed;

    public Cam4TabProvider(Cam4 cam4) {
        super(cam4);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();

        tabs.add(createTab("Female", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=MOST_VIEWERS&gender=female"));
        tabs.add(createTab("Male", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=MOST_VIEWERS&gender=male"));
        tabs.add(createTab("Trans", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=MOST_VIEWERS&gender=shemale"));
        tabs.add(createTab("Couples", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=MOST_VIEWERS&broadcastType=male_group&broadcastType=female_group&broadcastType=male_female_group"));
        tabs.add(createTab("HD", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=VIDEO_QUALITY&gender=female&broadcastType=female_group&broadcastType=solo&broadcastType=male_female_group&hd=true"));
        tabs.add(createTab("Mobile", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=MOST_VIEWERS&gender=female&broadcastType=female_group&broadcastType=solo&broadcastType=male_female_group&source=mobile"));
        tabs.add(createTab("New", site.getBaseUrl() + "/directoryCams?directoryJson=true&online=true&url=true&orderBy=MOST_VIEWERS&gender=female&broadcastType=female_group&broadcastType=solo&broadcastType=male_female_group&newPerformer=true"));

        followed = new Cam4FollowedTab((Cam4) site);
        followed.setRecorder(recorder);
        tabs.add(followed);

        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followed;
    }

    private Tab createTab(String name, String url) {
        var updateService = new Cam4UpdateService(url, false, (Cam4) site);
        var tab = new ThumbOverviewTab(name, updateService, site);
        tab.setImageAspectRatio(9.0 / 16.0);
        tab.setRecorder(recorder);
        return tab;
    }

}
