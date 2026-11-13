package ctbrec.ui.sites.myfreecams;

import ctbrec.Config;
import ctbrec.sites.mfc.MyFreeCams;
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

public class MyFreeCamsConfigUI extends AbstractConfigUI {
    private MyFreeCams myFreeCams;

    public MyFreeCamsConfigUI(MyFreeCams myFreeCams) {
        this.myFreeCams = myFreeCams;
    }

    @Override
    public Parent createConfigPanel() {
        var row = 0;
        var layout = SettingsTab.createGridLayout();
        var settings = Config.getInstance().getSettings();

        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(myFreeCams.getName()));
        enabled.setOnAction(e -> {
            if(enabled.isSelected()) {
                settings.disabledSites.remove(myFreeCams.getName());
            } else {
                settings.disabledSites.add(myFreeCams.getName());
            }
            save();
        });
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        var l = new Label("Active");
        l.labelForProperty().set(enabled);
        layout.add(l, 0, row);
        layout.add(enabled, 1, row++);

        var username = new TextField(Config.getInstance().getSettings().mfcUsername);
        username.setId("mfcUsername");
        username.setPrefWidth(300);
        username.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().mfcUsername)) {
                Config.getInstance().getSettings().mfcUsername = username.getText();
                myFreeCams.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        l = new Label("MyFreeCams User");
        l.labelForProperty().set(username);
        layout.add(l, 0, row);
        layout.add(username, 1, row++);

        var password = new PasswordField();
        password.setText(Config.getInstance().getSettings().mfcPassword);
        password.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().mfcPassword)) {
                Config.getInstance().getSettings().mfcPassword = password.getText();
                myFreeCams.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        l = new Label("MyFreeCams Password");
        l.labelForProperty().set(password);
        layout.add(l, 0, row);
        layout.add(password, 1, row++);

        var baseUrl = new TextField();
        baseUrl.setText(Config.getInstance().getSettings().mfcBaseUrl);
        baseUrl.textProperty().addListener((ob, o, n) -> {
            Config.getInstance().getSettings().mfcBaseUrl = baseUrl.getText();
            save();
        });
        GridPane.setFillWidth(baseUrl, true);
        GridPane.setHgrow(baseUrl, Priority.ALWAYS);
        GridPane.setColumnSpan(baseUrl, 2);
        l = new Label("MyFreeCams Base URL");
        l.labelForProperty().set(baseUrl);
        layout.add(l, 0, row);
        layout.add(baseUrl, 1, row++);

        var createAccount = new Button("Create new Account");
        createAccount.setOnAction(e -> DesktopIntegration.open(myFreeCams.getAffiliateLink()));
        layout.add(createAccount, 1, row++);
        GridPane.setColumnSpan(createAccount, 2);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> myFreeCams.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(username, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(password, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(baseUrl, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(createAccount, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));

        return layout;
    }
}
