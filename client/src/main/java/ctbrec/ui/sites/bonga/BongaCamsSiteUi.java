package ctbrec.ui.sites.bonga;

import ctbrec.sites.bonga.BongaCams;
import ctbrec.sites.bonga.BongaCamsHttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class BongaCamsSiteUi extends AbstractSiteUi {

    private static final Logger LOG = LoggerFactory.getLogger(BongaCamsSiteUi.class);
    private final BongaCams bongaCams;
    private BongaCamsTabProvider tabProvider;
    private BongaCamsConfigUI configUi;

    public BongaCamsSiteUi(BongaCams bongaCams) {
        this.bongaCams = bongaCams;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new BongaCamsTabProvider(bongaCams);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new BongaCamsConfigUI(bongaCams);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        boolean automaticLogin = bongaCams.login();
        if (automaticLogin) {
            return true;
        } else {
            // login with external browser window
            try {
                new BongaCamsElectronLoginDialog(bongaCams, bongaCams.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + bongaCams.getName(), e1);
            }

            BongaCamsHttpClient httpClient = (BongaCamsHttpClient) bongaCams.getHttpClient();
            boolean loggedIn = httpClient.checkLoginSuccess();
            if (loggedIn) {
                LOG.info("Logged in. User ID is {}", httpClient.getUserId());
            } else {
                LOG.info("Login failed");
            }
            return loggedIn;
        }
    }
}
