package ctbrec.ui.sites.fc2live;

import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class Fc2TabProvider extends AbstractTabProvider {

    private Fc2FollowedTab followed;

    public Fc2TabProvider(Fc2Live fc2live) {
        super(fc2live);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Online", Fc2Live.BASE_URL + "/adult/contents/allchannellist.php"));

        followed = new Fc2FollowedTab((Fc2Live) site);
        followed.setRecorder(site.getRecorder());
        tabs.add(followed);

        return tabs;
    }

    private Tab createTab(String title, String url) {
        var updateService = new Fc2UpdateService(url, (Fc2Live) site);
        var tab = new ThumbOverviewTab(title, updateService, site);
        tab.setRecorder(site.getRecorder());
        return tab;
    }

    @Override
    public Tab getFollowedTab() {
        return followed;
    }
}
