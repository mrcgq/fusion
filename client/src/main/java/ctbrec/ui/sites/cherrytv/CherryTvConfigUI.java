package ctbrec.ui.sites.cherrytv;

import ctbrec.Config;
import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.sites.AbstractConfigUI;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class CherryTvConfigUI extends AbstractConfigUI {
    private final CherryTv site;

    public CherryTvConfigUI(CherryTv cherryTv) {
        this.site = cherryTv;
    }

    @Override
    public Parent createConfigPanel() {
        var layout = SettingsTab.createGridLayout();
        var settings = Config.getInstance().getSettings();

        var row = 0;
        var l = new Label("Active");
        layout.add(l, 0, row);
        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(site.getName()));
        enabled.setOnAction(e -> {
            if (enabled.isSelected()) {
                settings.disabledSites.remove(site.getName());
            } else {
                settings.disabledSites.add(site.getName());
            }
            save();
        });
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(enabled, 1, row++);

        layout.add(new Label(site.getName() + " User"), 0, row);
        var username = new TextField(Config.getInstance().getSettings().cherryTvUsername);
        username.textProperty().addListener((ob, o, n) -> {
            if (!n.equals(Config.getInstance().getSettings().cherryTvUsername)) {
                Config.getInstance().getSettings().cherryTvUsername = username.getText();
                site.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        layout.add(username, 1, row++);

        layout.add(new Label(site.getName() + " Password"), 0, row);
        var password = new PasswordField();
        password.setText(Config.getInstance().getSettings().cherryTvPassword);
        password.textProperty().addListener((ob, o, n) -> {
            if (!n.equals(Config.getInstance().getSettings().cherryTvPassword)) {
                Config.getInstance().getSettings().cherryTvPassword = password.getText();
                site.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        layout.add(password, 1, row++);

        var createAccount = new Button("Create new Account");
        createAccount.setOnAction(e -> DesktopIntegration.open(site.getAffiliateLink()));
        layout.add(createAccount, 1, row++);
        GridPane.setColumnSpan(createAccount, 2);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> site.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(username, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(password, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(createAccount, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        return layout;
    }

}
