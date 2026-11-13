package ctbrec.ui.sites;

import ctbrec.Config;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.tabs.TabProvider;
import ctbrec.ui.tabs.recorded.RecordedModelsPerSiteTab;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

import java.util.List;

public abstract class AbstractTabProvider implements TabProvider {

    protected Recorder recorder;
    protected Site site;

    protected AbstractTabProvider(Site site) {
        this.site = site;
        this.recorder = site.getRecorder();
    }

    @Override
    public List<Tab> getTabs(Scene scene) {
        var tabs = getSiteTabs(scene);
        if (Config.getInstance().getSettings().recordedModelsPerSite) {
            var recordingTab = new RecordedModelsPerSiteTab("Recording", recorder, site);
            tabs.add(recordingTab);
        }
        return tabs;
    }

    protected abstract List<Tab> getSiteTabs(Scene scene);

    @Override
    public Tab getFollowedTab() {
        return null;
    }
}
