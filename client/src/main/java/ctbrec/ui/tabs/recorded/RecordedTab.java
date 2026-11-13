package ctbrec.ui.tabs.recorded;

import java.util.List;

import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.ShutdownListener;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Side;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;

public class RecordedTab extends Tab implements TabSelectionListener, ShutdownListener {

    private TabPane tabPane;
    private RecordedModelsTab recordedModelsTab;
    private RecordLaterTab recordLaterTab;

    public RecordedTab(Recorder recorder, List<Site> sites) {
        super("Recording");
        setClosable(false);

        recordedModelsTab = new RecordedModelsTab("Active", recorder, sites);
        recordLaterTab = new RecordLaterTab("Later", recorder, sites);

        tabPane = new TabPane();
        tabPane.setSide(Side.LEFT);
        tabPane.getTabs().addAll(recordedModelsTab, recordLaterTab);
        setContent(tabPane);

        // register changelistener to activate / deactivate tabs, when the user switches between them
        tabPane.getSelectionModel().selectedItemProperty().addListener((ChangeListener<Tab>) (ov, from, to) -> {
            if (from instanceof TabSelectionListener) {
                ((TabSelectionListener) from).deselected();
            }
            if (to instanceof TabSelectionListener) {
                ((TabSelectionListener) to).selected();
            }
        });
    }

    @Override
    public void selected() {
        var selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if(selectedTab instanceof TabSelectionListener) {
            ((TabSelectionListener) selectedTab).selected();
        }
    }

    @Override
    public void deselected() {
        var selectedTab = tabPane.getSelectionModel().getSelectedItem();
        if(selectedTab instanceof TabSelectionListener) {
            ((TabSelectionListener) selectedTab).deselected();
        }
    }

    @Override
    public void onShutdown() {
        recordedModelsTab.saveState();
        recordLaterTab.saveState();
    }
}
