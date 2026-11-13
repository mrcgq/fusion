package ctbrec.ui.settings.api;

import java.io.IOException;

import javafx.scene.Node;

public interface PreferencesStorage {

    void save(Preferences preferences) throws IOException;
    void load(Preferences preferences);

    Node createGui(Setting setting) throws NoSuchFieldException, IllegalAccessException;
}
