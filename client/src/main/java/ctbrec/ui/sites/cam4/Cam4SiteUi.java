package ctbrec.ui.sites.cam4;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.sites.cam4.Cam4;
import ctbrec.sites.cam4.Cam4HttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class Cam4SiteUi extends AbstractSiteUi {
    private static final Logger LOG = LoggerFactory.getLogger(Cam4SiteUi.class);

    private final Cam4 cam4;
    private Cam4TabProvider tabProvider;
    private Cam4ConfigUI configUi;

    public Cam4SiteUi(Cam4 cam4) {
        this.cam4 = cam4;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new Cam4TabProvider(cam4);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new Cam4ConfigUI(cam4);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        boolean automaticLogin = cam4.login();
        if (automaticLogin) {
            return true;
        } else {
            // login with external browser
            try {
                new Cam4ElectronLoginDialog(cam4.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + cam4.getName(), e1);
            }
            Cam4HttpClient httpClient = (Cam4HttpClient) cam4.getHttpClient();
            boolean loggedIn = httpClient.checkLoginSuccess();
            return loggedIn;
        }
    }
}
