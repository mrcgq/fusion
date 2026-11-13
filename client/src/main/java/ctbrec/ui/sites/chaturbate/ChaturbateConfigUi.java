package ctbrec.ui.sites.chaturbate;

import ctbrec.Config;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.sites.AbstractConfigUI;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

public class ChaturbateConfigUi extends AbstractConfigUI {
    private Chaturbate chaturbate;

    public ChaturbateConfigUi(Chaturbate chaturbate) {
        this.chaturbate = chaturbate;
    }

    @Override
    public Parent createConfigPanel() {
        var settings = Config.getInstance().getSettings();
        GridPane layout = SettingsTab.createGridLayout();

        var row = 0;
        var l = new Label("Active");
        layout.add(l, 0, row);
        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(chaturbate.getName()));
        enabled.setOnAction(e -> {
            if(enabled.isSelected()) {
                settings.disabledSites.remove(chaturbate.getName());
            } else {
                settings.disabledSites.add(chaturbate.getName());
            }
            save();
        });
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(enabled, 1, row++);

        layout.add(new Label("Chaturbate User"), 0, row);
        var username = new TextField(Config.getInstance().getSettings().chaturbateUsername);
        username.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().chaturbateUsername)) {
                Config.getInstance().getSettings().chaturbateUsername = n;
                chaturbate.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        layout.add(username, 1, row++);

        layout.add(new Label("Chaturbate Password"), 0, row);
        var password = new PasswordField();
        password.setText(Config.getInstance().getSettings().chaturbatePassword);
        password.textProperty().addListener((ob, o, n) -> {
            if(!n.equals(Config.getInstance().getSettings().chaturbatePassword)) {
                Config.getInstance().getSettings().chaturbatePassword = n;
                chaturbate.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        layout.add(password, 1, row++);

        layout.add(new Label("Chaturbate Base URL"), 0, row);
        var baseUrl = new TextField();
        baseUrl.setText(Config.getInstance().getSettings().chaturbateBaseUrl);
        baseUrl.textProperty().addListener((ob, o, n) -> {
            Config.getInstance().getSettings().chaturbateBaseUrl = baseUrl.getText();
            save();
        });
        GridPane.setFillWidth(baseUrl, true);
        GridPane.setHgrow(baseUrl, Priority.ALWAYS);
        GridPane.setColumnSpan(baseUrl, 2);
        layout.add(baseUrl, 1, row++);

        layout.add(new Label("Time between requests (ms)"), 0, row);
        var requestThrottle = new TextField(Integer.toString(Config.getInstance().getSettings().chaturbateMsBetweenRequests));
        requestThrottle.textProperty().addListener((ob, o, n) -> {
            int newValue = -1;
            try {
                newValue = Integer.parseInt(n);
            } catch (Exception e) {
                requestThrottle.setText(o);
                return;
            }
            if (newValue != Config.getInstance().getSettings().chaturbateMsBetweenRequests) {
                Config.getInstance().getSettings().chaturbateMsBetweenRequests = newValue;
                save();
            }
        });
        GridPane.setFillWidth(requestThrottle, true);
        GridPane.setHgrow(requestThrottle, Priority.ALWAYS);
        GridPane.setColumnSpan(requestThrottle, 2);
        layout.add(requestThrottle, 1, row++);

        var createAccount = new Button("Create new Account");
        createAccount.setOnAction(e -> DesktopIntegration.open(Chaturbate.REGISTRATION_LINK));
        layout.add(createAccount, 1, row++);
        GridPane.setColumnSpan(createAccount, 2);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> chaturbate.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row);
        GridPane.setColumnSpan(deleteCookies, 2);

        GridPane.setMargin(username, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(password, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(baseUrl, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(requestThrottle, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(createAccount, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));

        username.setPrefWidth(300);

        return layout;
    }
}
