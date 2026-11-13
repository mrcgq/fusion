package ctbrec.ui.sites.streamate;

import ctbrec.sites.streamate.Streamate;
import ctbrec.ui.sites.AbstractTabProvider;
import ctbrec.ui.tabs.ThumbOverviewTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.ArrayList;
import java.util.List;

public class StreamateTabProvider extends AbstractTabProvider {
    private ThumbOverviewTab followedTab;

    public StreamateTabProvider(Streamate streamate) {
        super(streamate);
    }

    @Override
    protected List<Tab> getSiteTabs(Scene scene) {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(createTab("Girls",     Streamate.NAIAD_URL + "/performers?domain=streamate.com&loggedInRec=1&boostedFilters=&country=US&language=en&index=default&filters=gender:f,ff%3Bonline:true"));
        tabs.add(createTab("Girls New", Streamate.NAIAD_URL + "/performers?domain=streamate.com&loggedInRec=1&boostedFilters=&country=US&language=en&index=default&filters=gender:f,ff%3Bnew:true%3Bonline:true"));
        tabs.add(createTab("Guys",      Streamate.NAIAD_URL + "/performers?domain=streamate.com&loggedInRec=1&boostedFilters=&country=US&language=en&index=default&filters=gender:m%3Bonline:true"));
        tabs.add(createTab("Couples",   Streamate.NAIAD_URL + "/performers?domain=streamate.com&loggedInRec=1&boostedFilters=&country=US&language=en&index=default&filters=gender:mf,mm,g%3Bonline:true"));
        tabs.add(createTab("Trans",     Streamate.NAIAD_URL + "/performers?domain=streamate.com&loggedInRec=1&boostedFilters=&country=US&language=en&index=default&filters=gender:tm2f,tf2m%3Bonline:true"));
        tabs.add(createTab("New",       Streamate.NAIAD_URL + "/performers?domain=streamate.com&loggedInRec=1&boostedFilters=&country=US&language=en&index=default&filters=new:true%3Bonline:true"));

        followedTab = new StreamateFollowedTab((Streamate) site);
        followedTab.setRecorder(recorder);
        tabs.add(followedTab);

        return tabs;
    }

    @Override
    public Tab getFollowedTab() {
        return followedTab;
    }

    private Tab createTab(String title, String url) {
        var updateService = new StreamateUpdateService((Streamate) site, url);
        var tab = new StreamateTab(title, updateService, site);
        tab.setRecorder(recorder);
        return tab;
    }
}
