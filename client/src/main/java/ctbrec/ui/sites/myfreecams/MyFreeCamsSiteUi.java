package ctbrec.ui.sites.myfreecams;

import java.io.IOException;

import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class MyFreeCamsSiteUi extends AbstractSiteUi {

    private MyFreeCamsTabProvider tabProvider;
    private MyFreeCamsConfigUI configUi;
    private MyFreeCams myFreeCams;

    public MyFreeCamsSiteUi(MyFreeCams myFreeCams) {
        this.myFreeCams = myFreeCams;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new MyFreeCamsTabProvider(myFreeCams);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new MyFreeCamsConfigUI(myFreeCams);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return myFreeCams.login();
    }

}
