package ctbrec.ui.tabs;

import ctbrec.sites.Site;
import ctbrec.ui.SiteUiFactory;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Side;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class SiteTabPane extends TabPane {

    public SiteTabPane(Site site, Scene scene) {
        setSide(Side.LEFT);

        // add all tabs
        var tabProvider = SiteUiFactory.getUi(site).getTabProvider();
        for (Tab tab : tabProvider.getTabs(scene)) {
            getTabs().add(tab);
        }

        // register changelistener to activate / deactivate tabs, when the user switches between them
        getSelectionModel().selectedItemProperty().addListener((ChangeListener<Tab>) (ov, from, to) -> {
            if (from instanceof TabSelectionListener) {
                ((TabSelectionListener) from).deselected();
            }
            if (to instanceof TabSelectionListener) {
                ((TabSelectionListener) to).selected();
            }
        });
    }

    public void selected() {
        var selectedTab = getSelectionModel().getSelectedItem();
        if (selectedTab instanceof TabSelectionListener) {
            ((TabSelectionListener) selectedTab).selected();
        }
    }

    public void deselected() {
        var selectedTab = getSelectionModel().getSelectedItem();
        if (selectedTab instanceof TabSelectionListener) {
            ((TabSelectionListener) selectedTab).deselected();
        }
    }
}
