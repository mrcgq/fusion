package ctbrec.ui.sites.streamate;

import java.io.IOException;

import ctbrec.sites.streamate.Streamate;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class StreamateSiteUi extends AbstractSiteUi {

    private StreamateTabProvider tabProvider;
    private StreamateConfigUI configUi;
    private Streamate streamate;

    public StreamateSiteUi(Streamate streamate) {
        this.streamate = streamate;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new StreamateTabProvider(streamate);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new StreamateConfigUI(streamate);
        }
        return configUi;
    }

    @Override
    public boolean login() throws IOException {
        return streamate.login();
    }

}
