package ctbrec.ui.settings.api;

import static java.util.Optional.*;

import ctbrec.StringUtil;
import ctbrec.ui.controls.Dialogs;
import javafx.beans.property.Property;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.control.Control;
import javafx.scene.control.Tooltip;

public class Setting {

    private String name;
    private String tooltip;
    private Property<?> property;
    private Node gui;
    private PreferencesStorage preferencesStorage;
    private boolean needsRestart = false;
    private ValueConverter converter;
    private ChangeListener<?> changeListener;

    protected Setting(String name, Property<?> property) {
        this.name = name;
        this.property = property;
    }

    protected Setting(String name, Node gui) {
        this.name = name;
        this.gui = gui;
    }

    public static Setting of(String name, Property<?> property) {
        return new Setting(name, property);
    }

    public static Setting of(String name, Property<?> property, String tooltip) {
        var setting = new Setting(name, property);
        setting.tooltip = tooltip;
        return setting;
    }

    public static Setting of(String name, Node gui) {
        var setting = new Setting(name, gui);
        return setting;
    }

    public String getName() {
        return name;
    }

    public String getKey() {
        if (getProperty() == null) {
            return "";
        } else {
            String key = getProperty().getName();
            if (StringUtil.isBlank(key)) {
                throw new IllegalStateException("Name for property of setting [" + name + "] is null");
            }
            return key;
        }
    }

    public String getTooltip() {
        return tooltip;
    }

    public Setting needsRestart() {
        needsRestart = true;
        return this;
    }

    public boolean doesNeedRestart() {
        return needsRestart;
    }

    @SuppressWarnings("rawtypes")
    public Property getProperty() {
        return property;
    }

    public Node getGui() {
        if (gui == null) {
            try {
                gui = preferencesStorage.createGui(this);
                if (gui instanceof Control && StringUtil.isNotBlank(tooltip)) {
                    var control = (Control) gui;
                    control.setTooltip(new Tooltip(tooltip));
                }
            } catch (NoSuchFieldException | IllegalAccessException e) {
                Dialogs.showError("Error", "Error creating settings GUI", e);
            }
        }
        return gui;
    }

    public void setPreferencesStorage(PreferencesStorage preferencesStorage) {
        this.preferencesStorage = preferencesStorage;
    }

    public boolean contains(String filter) {
        boolean contains = name.toLowerCase().contains(filter)
                || ofNullable(tooltip).orElse("").toLowerCase().contains(filter)
                || ofNullable(property).map(Property::getValue).map(Object::toString).orElse("").toLowerCase().contains(filter);
        return contains;
    }

    public Setting converter(ValueConverter converter) {
        this.converter = converter;
        return this;
    }

    public ValueConverter getConverter() {
        return converter;
    }

    public Setting onChange(ChangeListener<?> changeListener) {
        this.changeListener = changeListener;
        return this;
    }

    public ChangeListener<?> getChangeListener() {
        return changeListener;
    }
}
