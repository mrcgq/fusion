package ctbrec.ui.sites.jasmin;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.sites.jasmin.LiveJasminHttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class LiveJasminSiteUi extends AbstractSiteUi {

    private static final Logger LOG = LoggerFactory.getLogger(LiveJasminSiteUi.class);
    private final LiveJasmin liveJasmin;
    private LiveJasminTabProvider tabProvider;
    private LiveJasminConfigUi configUi;
    private long lastLoginTime = 0;

    public LiveJasminSiteUi(LiveJasmin liveJasmin) {
        this.liveJasmin = liveJasmin;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new LiveJasminTabProvider(liveJasmin);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new LiveJasminConfigUi(liveJasmin);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        // renew login every 30 min
        long now = System.currentTimeMillis();
        var renew = false;
        if ((now - lastLoginTime) > TimeUnit.MINUTES.toMillis(30)) {
            renew = true;
        }

        boolean automaticLogin = liveJasmin.login(renew);
        if (automaticLogin) {
            lastLoginTime = System.currentTimeMillis();
            return true;
        } else {
            lastLoginTime = System.currentTimeMillis();

            // login with external browser window
            try {
                new LiveJasminElectronLoginDialog(liveJasmin.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + liveJasmin.getName(), e1);
            }

            LiveJasminHttpClient httpClient = (LiveJasminHttpClient) liveJasmin.getHttpClient();
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
