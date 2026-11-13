package ctbrec.ui.sites.winktv;

import ctbrec.sites.winktv.WinkTv;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

import java.io.IOException;

public class WinkTvSiteUi extends AbstractSiteUi {

    private WinkTvTabProvider tabProvider;
    private WinkTvConfigUI configUi;
    private final WinkTv site;

    public WinkTvSiteUi(WinkTv site) {
        this.site = site;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new WinkTvTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new WinkTvConfigUI(site);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return site.login();
    }
}
