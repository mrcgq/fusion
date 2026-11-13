package ctbrec.ui;

import ctbrec.Model;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabProvider;

import java.io.IOException;

public interface SiteUI {

    TabProvider getTabProvider();

    ConfigUI getConfigUI();

    boolean login() throws IOException;

    boolean play(Model model);
}
