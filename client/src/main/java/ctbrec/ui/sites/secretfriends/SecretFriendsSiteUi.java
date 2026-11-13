package ctbrec.ui.sites.secretfriends;

import ctbrec.sites.secretfriends.SecretFriends;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

import java.io.IOException;

public class SecretFriendsSiteUi extends AbstractSiteUi {

    private SecretFriendsTabProvider tabProvider;
    private SecretFriendsConfigUI configUi;
    private final SecretFriends site;

    public SecretFriendsSiteUi(SecretFriends site) {
        this.site = site;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new SecretFriendsTabProvider(site);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new SecretFriendsConfigUI(site);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        return site.login();
    }
}
