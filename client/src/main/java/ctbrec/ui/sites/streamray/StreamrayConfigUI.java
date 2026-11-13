package ctbrec.ui.sites.streamray;

import ctbrec.Config;
import ctbrec.sites.streamray.Streamray;
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

public class StreamrayConfigUI extends AbstractConfigUI {
    private final Streamray site;

    public StreamrayConfigUI(Streamray site) {
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
        
        l = new Label("Record Goal/Tipping shows");
        layout.add(l, 0, row);
        var cb = new CheckBox();
        cb.setSelected(settings.streamrayRecordGoalShows);
        cb.setOnAction(e -> {
            settings.streamrayRecordGoalShows = cb.isSelected();
            save();
        });
        GridPane.setMargin(cb, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(cb, 1, row++);
        row++;

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> site.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        return layout;
    }

}
