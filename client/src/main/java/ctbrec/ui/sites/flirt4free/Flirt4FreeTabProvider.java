package ctbrec.ui.sites.flirt4free;

import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.sites.flirt4free.Flirt4FreeModel;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class Flirt4FreeTabProvider extends AbstractTabProvider {

    private final ThumbOverviewTab followedTab;

    public Flirt4FreeTabProvider(Flirt4Free flirt4Free) {
        super(flirt4Free);
        followedTab = new Flirt4FreeFavoritesTab(flirt4Free);
        followedTab.setRecorder(flirt4Free.getRecorder());
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls", site.getBaseUrl() + "/live/girls/?tpl=index2&model=json", m -> true));
        tabs.add(createTab("New Girls", site.getBaseUrl() + "/live/girls/?tpl=index2&model=json", Flirt4FreeModel::isNew));
        tabs.add(createTab("Boys", site.getBaseUrl() + "/live/guys/?tpl=index2&model=json", m -> true));
        tabs.add(createTab("Couples", site.getBaseUrl() + "/live/couples/?tpl=index2&model=json", m -> m.getCategories().contains("2")));
        tabs.add(createTab("Trans", site.getBaseUrl() + "/live/trans/?tpl=index2&model=json", m -> true));
        tabs.add(followedTab);
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private ThumbOverviewTab createTab(String title, String url, Predicate<Flirt4FreeModel> filter) {
        var s = new Flirt4FreeUpdateService((Flirt4Free) site, url, filter);
        var tab = new ThumbOverviewTab(title, s, site);
        tab.setRecorder(site.getRecorder());
        s.setPeriod(Duration.seconds(60));
        return tab;
    }
}
