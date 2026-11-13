package ctbrec.ui.sites.stripchat;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.sites.stripchat.Stripchat;
import ctbrec.sites.stripchat.StripchatHttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class StripchatSiteUi extends AbstractSiteUi {

    private static final Logger LOG = LoggerFactory.getLogger(StripchatSiteUi.class);

    private StripchatTabProvider tabProvider;
    private StripchatConfigUI configUi;
    private Stripchat site;

    public StripchatSiteUi(Stripchat stripchat) {
        this.site = stripchat;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new StripchatTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new StripchatConfigUI(site);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        boolean automaticLogin = site.login();
        if (automaticLogin) {
            return true;
        } else {
            // login with external browser
            try {
                new StripchatElectronLoginDialog(site.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + site.getName(), e1);
            }

            StripchatHttpClient httpClient = (StripchatHttpClient) site.getHttpClient();
            boolean loggedIn = httpClient.checkLoginSuccess();
            return loggedIn;
        }
    }
}
