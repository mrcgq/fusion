package ctbrec.ui.sites;

import ctbrec.Model;
import ctbrec.ui.Player;
import ctbrec.ui.SiteUI;

public abstract class AbstractSiteUi implements SiteUI {
    @Override
    public boolean play(Model model) {
        return Player.play(model);
    }
}
