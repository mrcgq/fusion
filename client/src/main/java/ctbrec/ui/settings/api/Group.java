package ctbrec.ui.settings.api;

import java.util.Objects;

public class Group {

    public static final String DEFAULT = "default";
    private String name;
    private Setting[] settings;

    private Group(String name, Setting...settings) {
        this.name = name;
        this.settings = settings;
    }

    public static Group of(Setting...settings) {
        return new Group(DEFAULT, settings);
    }

    public static Group of(String name, Setting...settings) {
        return new Group(name, settings);
    }

    String getName() {
        return name;
    }

    Setting[] getSettings() {
        return settings;
    }

    boolean isDefault() {
        return Objects.equals(name,  DEFAULT);
    }

    public boolean contains(String filter) {
        return name.toLowerCase().contains(filter) || settingsContain(filter);
    }

    private boolean settingsContain(String filter) {
        var contains = false;
        if (settings != null) {
            for (Setting setting : settings) {
                if (setting.contains(filter)) {
                    contains = true;
                }
            }
        }
        return contains;
    }
}
