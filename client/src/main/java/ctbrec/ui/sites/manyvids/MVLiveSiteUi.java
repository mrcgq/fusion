package ctbrec.ui.sites.manyvids;

import java.io.IOException;

import ctbrec.sites.manyvids.MVLive;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class MVLiveSiteUi extends AbstractSiteUi {

    private final MVLive mvlive;
    private MVLiveTabProvider tabProvider;
    private MVLiveConfigUi configUi;

    public MVLiveSiteUi(MVLive mvlive) {
        this.mvlive = mvlive;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new MVLiveTabProvider(mvlive);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new MVLiveConfigUi(mvlive);
        }
        return configUi;
    }

    @Override
    public boolean login() throws IOException {
        return false;
    }

}
