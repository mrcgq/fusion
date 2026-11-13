package ctbrec.ui.sites.dreamcam;

import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

import java.io.IOException;

public class DreamcamSiteUi extends AbstractSiteUi {

    private DreamcamTabProvider tabProvider;
    private DreamcamConfigUI configUi;
    private final Dreamcam site;

    public DreamcamSiteUi(Dreamcam site) {
        this.site = site;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new DreamcamTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new DreamcamConfigUI(site);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return site.login();
    }
}
