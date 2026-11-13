package ctbrec.ui.sites.dreamcam;

import ctbrec.Config;
import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.sites.AbstractConfigUI;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.RadioButton;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;

public class DreamcamConfigUI extends AbstractConfigUI {
    private final Dreamcam site;

    public DreamcamConfigUI(Dreamcam site) {
        this.site = site;
    }

    @Override
    public Parent createConfigPanel() {
        GridPane layout = SettingsTab.createGridLayout();
        var settings = Config.getInstance().getSettings();

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
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(enabled, 1, row++);

        row++;
        l = new Label("VR Mode");
        layout.add(l, 0, row);
        var vr = new CheckBox();
        vr.setSelected(settings.dreamcamVR);
        vr.setOnAction(e -> {
            settings.dreamcamVR = vr.isSelected();
            save();
        });
        GridPane.setMargin(vr, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(vr, 1, row++);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> site.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        return layout;
    }

}
