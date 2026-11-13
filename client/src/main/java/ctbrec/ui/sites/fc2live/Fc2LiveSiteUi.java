package ctbrec.ui.sites.fc2live;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.sites.fc2live.Fc2Model;
import ctbrec.ui.JavaFxModel;
import ctbrec.ui.Player;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.sites.AbstractSiteUi;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

public class Fc2LiveSiteUi extends AbstractSiteUi {
    private static final Logger LOG = LoggerFactory.getLogger(Fc2LiveSiteUi.class);
    private final Fc2Live fc2live;
    private Fc2TabProvider tabProvider;
    private Fc2LiveConfigUI configUi;

    public Fc2LiveSiteUi(Fc2Live fc2live) {
        this.fc2live = fc2live;
    }

    @Override
    public TabProvider getTabProvider() {
        if (tabProvider == null) {
            this.tabProvider = new Fc2TabProvider(fc2live);
        }
        return tabProvider;
    }

    @Override
    public ConfigUI getConfigUI() {
        if (configUi == null) {
            this.configUi = new Fc2LiveConfigUI(fc2live);
        }
        return configUi;
    }

    @Override
    public boolean login() throws IOException {
        return fc2live.login();
    }

    @Override
    public boolean play(Model model) {
        GlobalThreadPool.submit(() -> {
            Fc2Model m;
            if (model instanceof JavaFxModel) {
                m = (Fc2Model) ((JavaFxModel) model).getDelegate();
            } else {
                m = (Fc2Model) model;
            }
            try {
                m.openWebsocket();
                LOG.debug("Starting player for {}", model);
                Player.play(model, false);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                handleException(e);
            } catch (IOException e) {
                handleException(e);
            } finally {
                m.closeWebsocket();
            }
        });
        return true;
    }

    private void handleException(Exception e) {
        LOG.error("Error playing the stream", e);
        Dialogs.showError("Player", "Error playing the stream", e);
    }
}
