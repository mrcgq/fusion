package ctbrec.ui.sites.camsoda;

import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.camsoda.CamsodaModel;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import static ctbrec.sites.camsoda.Camsoda.BASE_URI;

public class CamsodaTabProvider extends AbstractTabProvider {

    private static final String API_URL = BASE_URI + "/api/v1/browse/online";
    CamsodaFollowedTab followedTab;

    public CamsodaTabProvider(Camsoda camsoda) {
        super(camsoda);
        followedTab = new CamsodaFollowedTab("Followed", camsoda);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("All", API_URL, m -> true));
        tabs.add(createTab("New", API_URL, CamsodaModel::isNew));
        tabs.add(createTab("Female", API_URL, m -> Objects.equals("f", m.getGender())));
        tabs.add(createTab("Male", API_URL, m -> Objects.equals("m", m.getGender())));
        tabs.add(createTab("Couples", API_URL, m -> Objects.equals("c", m.getGender())));
        tabs.add(createTab("Trans", API_URL, m -> Objects.equals("t", m.getGender())));
        tabs.add(createTab("Voyeur", API_URL, CamsodaModel::isVoyeur));
        followedTab.setRecorder(recorder);
        followedTab.setScene(scene);
        tabs.add(followedTab);
        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private Tab createTab(String title, String url, Predicate<CamsodaModel> filter) {
        var updateService = new CamsodaUpdateService(url, false, (Camsoda) site, filter);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(recorder);
        return tab;
    }

}
