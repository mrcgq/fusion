package ctbrec.ui.sites.jasmin;

import ctbrec.Config;
import ctbrec.sites.jasmin.LiveJasmin;
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

public class LiveJasminConfigUi extends AbstractConfigUI {
    private LiveJasmin liveJasmin;

    public LiveJasminConfigUi(LiveJasmin liveJasmin) {
        this.liveJasmin = liveJasmin;
    }

    @Override
    public Parent createConfigPanel() {
        var settings = Config.getInstance().getSettings();
        var layout = SettingsTab.createGridLayout();

        var row = 0;
        var l = new Label("Active");
        layout.add(l, 0, row);
        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(liveJasmin.getName()));
        enabled.setOnAction(e -> {
            if(enabled.isSelected()) {
                settings.disabledSites.remove(liveJasmin.getName());
            } else {
                settings.disabledSites.add(liveJasmin.getName());
            }
            save();
        });
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(enabled, 1, row++);

        layout.add(new Label("LiveJasmin User"), 0, row);
        var username = new TextField(Config.getInstance().getSettings().livejasminUsername);
        username.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().livejasminUsername)) {
                Config.getInstance().getSettings().livejasminUsername = n;
                liveJasmin.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        layout.add(username, 1, row++);

        layout.add(new Label("LiveJasmin Password"), 0, row);
        var password = new PasswordField();
        password.setText(Config.getInstance().getSettings().livejasminPassword);
        password.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().livejasminPassword)) {
                Config.getInstance().getSettings().livejasminPassword = n;
                liveJasmin.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        layout.add(password, 1, row++);

        layout.add(new Label("LiveJasmin Base URL"), 0, row);
        var baseUrl = new TextField();
        baseUrl.setText(Config.getInstance().getSettings().livejasminBaseUrl);
        baseUrl.textProperty().addListener((ob, o, n) -> {
            Config.getInstance().getSettings().livejasminBaseUrl = baseUrl.getText();
            save();
        });
        GridPane.setFillWidth(baseUrl, true);
        GridPane.setHgrow(baseUrl, Priority.ALWAYS);
        GridPane.setColumnSpan(baseUrl, 2);
        layout.add(baseUrl, 1, row++);

        var createAccount = new Button("Create new Account");
        createAccount.setOnAction(e -> DesktopIntegration.open(liveJasmin.getAffiliateLink()));
        layout.add(createAccount, 1, row++);
        GridPane.setColumnSpan(createAccount, 2);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> liveJasmin.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(username, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(password, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(baseUrl,  new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(createAccount, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));

        username.setPrefWidth(300);

        return layout;
    }
}
