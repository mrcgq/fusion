package ctbrec.ui.sites.fc2live;

import ctbrec.Config;
import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.sites.AbstractConfigUI;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class Fc2LiveConfigUI extends AbstractConfigUI {
    private Fc2Live fc2live;

    public Fc2LiveConfigUI(Fc2Live fc2live) {
        this.fc2live = fc2live;
    }

    @Override
    public Parent createConfigPanel() {
        var layout = SettingsTab.createGridLayout();
        var settings = Config.getInstance().getSettings();

        var row = 0;
        var l = new Label("Active");
        layout.add(l, 0, row);
        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(fc2live.getName()));
        enabled.setOnAction(e -> {
            if(enabled.isSelected()) {
                settings.disabledSites.remove(fc2live.getName());
            } else {
                settings.disabledSites.add(fc2live.getName());
            }
            save();
        });
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(enabled, 1, row++);

        layout.add(new Label("FC2Live User"), 0, row);
        var username = new TextField(settings.fc2liveUsername);
        username.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().fc2liveUsername)) {
                Config.getInstance().getSettings().fc2liveUsername = username.getText();
                fc2live.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        layout.add(username, 1, row++);

        layout.add(new Label("FC2Live Password"), 0, row);
        var password = new PasswordField();
        password.setText(settings.fc2livePassword);
        password.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().fc2livePassword)) {
                Config.getInstance().getSettings().fc2livePassword = password.getText();
                fc2live.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        layout.add(password, 1, row++);

        var createAccount = new Button("Create new Account");
        createAccount.setOnAction(e -> DesktopIntegration.open(fc2live.getAffiliateLink()));
        layout.add(createAccount, 1, row++);
        GridPane.setColumnSpan(createAccount, 2);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> fc2live.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(username, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(password, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(createAccount, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        return layout;
    }

}
