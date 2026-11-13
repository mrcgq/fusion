package ctbrec.ui.sites.streamray;

import ctbrec.sites.streamray.Streamray;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

import java.io.IOException;

public class StreamraySiteUi extends AbstractSiteUi {

    private StreamrayTabProvider tabProvider;
    private StreamrayConfigUI configUi;
    private final Streamray site;

    public StreamraySiteUi(Streamray site) {
        this.site = site;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new StreamrayTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new StreamrayConfigUI(site);
        }
        return configUi;
    }

    @Override
    public boolean login() throws IOException {
        return site.login();
    }
}
