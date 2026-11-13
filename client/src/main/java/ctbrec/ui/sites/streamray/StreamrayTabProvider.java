package ctbrec.ui.sites.streamray;

import ctbrec.sites.streamray.Streamray;
import ctbrec.sites.streamray.StreamrayModel;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

public class StreamrayTabProvider extends AbstractTabProvider {

    private final Tab followedTab;

    public StreamrayTabProvider(Streamray site) {
        super(site);
        followedTab = favoritesTab();
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls", m -> Objects.equals("F", m.getGender())));
        tabs.add(createTab("Boys", m -> Objects.equals("M", m.getGender())));
        tabs.add(createTab("Trans", m -> Objects.equals("TS", m.getGender())));
        tabs.add(createTab("New", StreamrayModel::isNew));
        tabs.add(followedTab);
        return tabs;
    }

    private Tab createTab(String title, Predicate<StreamrayModel> filter) {
        var updateService = new StreamrayUpdateService((Streamray) site, filter);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setImageAspectRatio(9.0 / 16.0);
        tab.setRecorder(recorder);
        return tab;
    }

    private Tab favoritesTab() {
        var updateService = new StreamrayFavoritesService((Streamray) site);
        var tab = new StreamrayFavoritesTab("Favorites", updateService, (Streamray) site);
        tab.setImageAspectRatio(9.0 / 16.0);
        tab.setRecorder(recorder);
        return tab;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }
}
