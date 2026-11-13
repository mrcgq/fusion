package ctbrec.ui.sites.cherrytv;

import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class CherryTvSiteUi extends AbstractSiteUi {
    private static final Logger LOG = LoggerFactory.getLogger(CherryTvSiteUi.class);

    private final CherryTv cherryTv;
    private CherryTvTabProvider tabProvider;
    private CherryTvConfigUI configUi;

    public CherryTvSiteUi(CherryTv cherryTv) {
        this.cherryTv = cherryTv;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new CherryTvTabProvider(cherryTv);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new CherryTvConfigUI(cherryTv);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return cherryTv.login();
    }
}
