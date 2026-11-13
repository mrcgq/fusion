package ctbrec.ui.sites.chaturbate;

import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.chaturbate.ChaturbateHttpClient;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ChaturbateSiteUi extends AbstractSiteUi {

    private static final Logger LOG = LoggerFactory.getLogger(ChaturbateSiteUi.class);

    private final Chaturbate chaturbate;
    private ChaturbateTabProvider tabProvider;
    private ChaturbateConfigUi configUi;

    public ChaturbateSiteUi(Chaturbate chaturbate) {
        this.chaturbate = chaturbate;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            tabProvider = new ChaturbateTabProvider(chaturbate);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            configUi = new ChaturbateConfigUi(chaturbate);
        }
        return configUi;
    }

    @Override
    public synchronized boolean login() throws IOException {
        boolean automaticLogin = false;
        try {
            automaticLogin = chaturbate.login();
        } catch (Exception e) {
            LOG.debug("Automatic login failed", e);
        }
        if (automaticLogin) {
            return true;
        } else {
            // login with external browser window
            try {
                new ChaturbateElectronLoginDialog(chaturbate, chaturbate.getHttpClient().getCookieJar());
            } catch (Exception e1) {
                LOG.error("Error logging in with external browser", e1);
                Dialogs.showError("Login error", "Couldn't login to " + chaturbate.getName(), e1);
            }

            ChaturbateHttpClient httpClient = (ChaturbateHttpClient) chaturbate.getHttpClient();
            return httpClient.checkLogin();
        }
    }

}
