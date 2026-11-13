package ctbrec.ui.sites.stripchat;

import ctbrec.Config;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.sites.AbstractConfigUI;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

public class StripchatConfigUI extends AbstractConfigUI {
    private final Stripchat stripchat;

    public StripchatConfigUI(Stripchat stripchat) {
        this.stripchat = stripchat;
    }

    @Override
    public Parent createConfigPanel() {
        GridPane layout = SettingsTab.createGridLayout();
        var settings = Config.getInstance().getSettings();

        var row = 0;
        var l = new Label("Active");
        layout.add(l, 0, row);
        var enabled = new CheckBox();
        enabled.setSelected(!settings.disabledSites.contains(stripchat.getName()));
        enabled.setOnAction(e -> {
            if (enabled.isSelected()) {
                settings.disabledSites.remove(stripchat.getName());
            } else {
                settings.disabledSites.add(stripchat.getName());
            }
            save();
        });
        GridPane.setMargin(enabled, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(enabled, 1, row++);

        l = new Label("Site");
        layout.add(l, 0, row);
        var toggleGroup = new ToggleGroup();
        var optionA = new RadioButton("Stripchat");
        optionA.setSelected(!Config.getInstance().getSettings().stripchatUseXhamster);
        optionA.setToggleGroup(toggleGroup);
        var optionB = new RadioButton("xHamsterLive");
        optionB.setSelected(!optionA.isSelected());
        optionB.setToggleGroup(toggleGroup);
        optionA.selectedProperty().addListener((obs, oldV, newV) -> {
            Config.getInstance().getSettings().stripchatUseXhamster = !newV;
            save();
        });
        var hbox = new HBox();
        hbox.getChildren().addAll(optionA, optionB);
        HBox.setMargin(optionA, new Insets(5));
        HBox.setMargin(optionB, new Insets(5));
        GridPane.setMargin(hbox, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(hbox, 1, row++);

        layout.add(new Label("Stripchat User"), 0, row);
        var username = new TextField(Config.getInstance().getSettings().stripchatUsername);
        username.textProperty().addListener((ob, o, n) -> {
            if (!n.equals(Config.getInstance().getSettings().stripchatUsername)) {
                Config.getInstance().getSettings().stripchatUsername = username.getText();
                stripchat.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(username, true);
        GridPane.setHgrow(username, Priority.ALWAYS);
        GridPane.setColumnSpan(username, 2);
        layout.add(username, 1, row++);

        layout.add(new Label("Stripchat Password"), 0, row);
        var password = new PasswordField();
        password.setText(Config.getInstance().getSettings().stripchatPassword);
        password.textProperty().addListener((ob, o, n) -> {
            if (!n.equals(Config.getInstance().getSettings().stripchatPassword)) {
                Config.getInstance().getSettings().stripchatPassword = password.getText();
                stripchat.getHttpClient().logout();
                save();
            }
        });
        GridPane.setFillWidth(password, true);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setColumnSpan(password, 2);
        layout.add(password, 1, row++);

        var createAccount = new Button("Create new Account");
        createAccount.setOnAction(e -> DesktopIntegration.open(stripchat.getAffiliateLink()));
        layout.add(createAccount, 1, row++);
        GridPane.setColumnSpan(createAccount, 2);

        var deleteCookies = new Button("Delete Cookies");
        deleteCookies.setOnAction(e -> stripchat.getHttpClient().clearCookies());
        layout.add(deleteCookies, 1, row++);
        GridPane.setColumnSpan(deleteCookies, 2);

        row++;
        l = new Label("Get VR stream if available");
        layout.add(l, 0, row);
        var vr = new CheckBox();
        vr.setSelected(settings.stripchatVR);
        vr.setOnAction(e -> {
            settings.stripchatVR = vr.isSelected();
            save();
        });
        GridPane.setMargin(vr, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        layout.add(vr, 1, row);

        GridPane.setMargin(username, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(password, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(createAccount, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        GridPane.setMargin(deleteCookies, new Insets(0, 0, 0, SettingsTab.CHECKBOX_MARGIN));
        return layout;
    }

}
