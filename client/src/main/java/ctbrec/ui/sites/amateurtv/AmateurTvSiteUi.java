package ctbrec.ui.sites.amateurtv;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.sites.amateurtv.AmateurTvHttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class AmateurTvSiteUi extends AbstractSiteUi {

    private static final Logger LOG = LoggerFactory.getLogger(AmateurTvSiteUi.class);

    private final AmateurTv site;
    private AmateurTvTabProvider tabProvider;
    private AmateurTvConfigUI configUi;

    public AmateurTvSiteUi(AmateurTv amateurTv) {
        this.site = amateurTv;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new AmateurTvTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new AmateurTvConfigUI(site);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        if (!site.credentialsAvailable()) {
            return false;
        }

        boolean automaticLogin = site.login();
        if (automaticLogin) {
            return true;
        } else {
            // login with external browser window
            try {
                new AmateurTvElectronLoginDialog(site.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + site.getName(), e1);
            }

            AmateurTvHttpClient httpClient = (AmateurTvHttpClient) site.getHttpClient();
            return httpClient.checkLoginSuccess();
        }
    }
}
