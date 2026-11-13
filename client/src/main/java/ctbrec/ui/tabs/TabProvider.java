package ctbrec.ui.tabs;

import java.util.List;

import javafx.scene.Scene;
import javafx.scene.control.Tab;

public interface TabProvider {

    public List<Tab> getTabs(Scene scene);
    public Tab getFollowedTab();
}
