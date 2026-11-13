package ctbrec.ui.sites.winktv;

import ctbrec.sites.winktv.WinkTv;
import ctbrec.sites.winktv.WinkTvModel;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class WinkTvTabProvider extends AbstractTabProvider {

    public WinkTvTabProvider(WinkTv site) {
        super(site);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Live", Predicate.not(WinkTvModel::isAdult)));
        // adult streams are only available with korean age verification, you have to be logged in
        //tabs.add(createTab("Adult", WinkTvModel::isAdult));
        return tabs;
    }

    private Tab createTab(String title, Predicate<WinkTvModel> filter) {
        var updateService = new WinkTvUpdateService((WinkTv) site, filter);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setImageAspectRatio(9.0 / 16.0);
        tab.setRecorder(recorder);
        return tab;
    }

}
