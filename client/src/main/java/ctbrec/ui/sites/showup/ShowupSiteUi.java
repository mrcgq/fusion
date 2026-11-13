package ctbrec.ui.sites.showup;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.sites.showup.Showup;
import ctbrec.sites.showup.ShowupHttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class ShowupSiteUi extends AbstractSiteUi {

    private static final Logger LOG = LoggerFactory.getLogger(ShowupSiteUi.class);

    private final Showup site;
    private ConfigUI configUi;
    private TabProvider tabProvider;

    public ShowupSiteUi(Showup site) {
        this.site = site;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new ShowupTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new ShowupConfigUI(site);
        }
        return configUi;
    }

    @Override
    public boolean login() throws IOException {
        boolean automaticLogin = site.login();
        if (automaticLogin) {
            return true;
        } else {
            // login with external browser window
            try {
                new ShowupElectronLoginDialog(site.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + site.getName(), e1);
            }

            ShowupHttpClient httpClient = (ShowupHttpClient) site.getHttpClient();
            boolean loggedIn = httpClient.checkLoginSuccess();
            if (loggedIn) {
                LOG.info("Logged in");
            } else {
                LOG.info("Login failed");
            }
            return loggedIn;
        }
    }
}
