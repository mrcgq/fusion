package ctbrec.ui.sites.dreamcam;

import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.sites.dreamcam.DreamcamModel;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import java.util.ArrayList;
import java.util.List;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

public class DreamcamTabProvider extends AbstractTabProvider {
    private final static String API_URL = "https://bss.dreamcamtrue.com/api/clients/v1/broadcasts?partnerId=dreamcam_oauth2&show-offline=false&stream-types=video2D&include-tags=false&include-tip-menu=false";

    public DreamcamTabProvider(Dreamcam site) {
        super(site);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls",   API_URL + "&tag-categories=girls"));
        tabs.add(createTab("Boys",    API_URL + "&tag-categories=men"));
        tabs.add(createTab("Couples", API_URL + "&tag-categories=couples"));
        tabs.add(createTab("Trans",   API_URL + "&tag-categories=trans"));
        return tabs;
    }

    private Tab createTab(String title, String url) {
        var updateService = new DreamcamUpdateService((Dreamcam) site, url);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setImageAspectRatio(10.0 / 16.0);
        tab.setRecorder(recorder);
        return tab;
    }

}
