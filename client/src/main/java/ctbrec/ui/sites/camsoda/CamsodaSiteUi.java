package ctbrec.ui.sites.camsoda;

import java.io.IOException;

import ctbrec.sites.camsoda.Camsoda;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class CamsodaSiteUi extends AbstractSiteUi {

    private final Camsoda camsoda;
    private CamsodaTabProvider tabProvider;
    private CamsodaConfigUI configUi;

    public CamsodaSiteUi(Camsoda camsoda) {
        this.camsoda = camsoda;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new CamsodaTabProvider(camsoda);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new CamsodaConfigUI(camsoda);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        boolean automaticLogin = camsoda.login();
        return automaticLogin;
    }
}
