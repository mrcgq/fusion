package ctbrec.ui.sites.xlovecam;

import java.io.IOException;

import ctbrec.sites.xlovecam.XloveCam;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class XloveCamSiteUi extends AbstractSiteUi {

    private final XloveCam site;
    private XloveCamTabProvider tabProvider;
    private XloveCamConfigUI configUi;

    public XloveCamSiteUi(XloveCam xloveCam) {
        this.site = xloveCam;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new XloveCamTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new XloveCamConfigUI(site);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        if (!site.credentialsAvailable()) {
            return false;
        }
        return site.getHttpClient().login();
    }
}
