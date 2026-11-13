package ctbrec.ui.sites.flirt4free;

import java.io.IOException;

import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class Flirt4FreeSiteUi extends AbstractSiteUi {

    private final Flirt4Free flirt4Free;
    private Flirt4FreeTabProvider tabProvider;
    private Flirt4FreeConfigUI configUi;

    public Flirt4FreeSiteUi(Flirt4Free flirt4Free) {
        this.flirt4Free = flirt4Free;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new Flirt4FreeTabProvider(flirt4Free);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new Flirt4FreeConfigUI(flirt4Free);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return flirt4Free.login();
    }
}
