package ctbrec.ui.sites.manyvids;

import ctbrec.Config;
import ctbrec.sites.manyvids.MVLive;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.sites.AbstractConfigUI;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

public class MVLiveConfigUi extends AbstractConfigUI {
    private MVLive site;

    public MVLiveConfigUi(MVLive site) {
        this.site = site;
    }

    @Override
    public Parent createConfigPanel() {
        var settings = Config.getInstance().getSettings();
        GridPane layout = SettingsTab.createGridLayout();

        var row = 0;
        var l = new Label("Active");
        layout.add(l, 0, row);
        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(site.getName()));
        enabled.setOnAction(e -> {
            if(enabled.isSelected()) {
                settings.disabledSites.remove(site.getName());
            } else {
                settings.disabledSites.add(site.getName());
            }
            save();
        });
        layout.add(enabled, 1, row++);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> site.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        return layout;
    }
}
