package ctbrec.ui.tabs;

import ctbrec.sites.Site;
import javafx.scene.Scene;
import javafx.scene.control.Tab;

public class SiteTab extends Tab implements TabSelectionListener {

    private SiteTabPane siteTabPane;

    public SiteTab(Site site, Scene scene) {
        super(site.getTitle());
        setClosable(false);
        siteTabPane = new SiteTabPane(site, scene);
        setContent(siteTabPane);
    }

    @Override
    public void selected() {
        siteTabPane.selected();
    }

    @Override
    public void deselected() {
        siteTabPane.deselected();
    }
}
