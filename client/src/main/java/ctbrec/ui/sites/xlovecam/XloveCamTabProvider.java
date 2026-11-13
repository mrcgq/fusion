package ctbrec.ui.sites.xlovecam;

import ctbrec.sites.xlovecam.XloveCam;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class XloveCamTabProvider extends AbstractTabProvider {

    private final XloveCam xloveCam;

    private static final String FILTER_PARAM = "config[filter][10][]";
    private static final String FILTER_PARAM_NEW = "config[filter][100522][]";

    public XloveCamTabProvider(XloveCam xloveCam) {
        super(xloveCam);
        this.xloveCam = xloveCam;
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();

        // all
        var updateService = new XloveCamUpdateService(xloveCam, Collections.emptyMap());
        tabs.add(createTab("All", updateService));

        // new
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM_NEW, "3"));
        tabs.add(createTab("New", updateService));

        // Young Women
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "1"));
        tabs.add(createTab("Young Women", updateService));

        // Ladies
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "13"));
        tabs.add(createTab("Ladies", updateService));

        // Mature
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "6"));
        tabs.add(createTab("Mature Female", updateService));

        // Couples
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "2"));
        tabs.add(createTab("Couples", updateService));

        // Lesbian
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "3"));
        tabs.add(createTab("Lesbian", updateService));

        // Male
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "7"));
        tabs.add(createTab("Male", updateService));

        // Trans
        updateService = new XloveCamUpdateService(xloveCam, Map.of(FILTER_PARAM, "5"));
        tabs.add(createTab("Trans", updateService));

        return tabs;
    }

    private Tab createTab(String title, PaginatedScheduledService updateService) {
        var tab = new ThumbOverviewTab(title, updateService, xloveCam);
        tab.setRecorder(recorder);
        return tab;
    }

}
