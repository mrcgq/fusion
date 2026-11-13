package ctbrec.ui.tabs.recorded;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.action.PauseAction;
import ctbrec.ui.action.ResumeAction;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.event.ActionEvent;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RecordedModelsPerSiteTab extends RecordedModelsTab implements TabSelectionListener {

    public RecordedModelsPerSiteTab(String title, Recorder recorder, Site site) {
        super(title, recorder, List.of(site));
        filter(site.getName());
    }

    @Override
    protected void createGui() {
        super.createGui();
        addModelBox.getChildren().remove(toggleRecording);
        addModelBox.getChildren().remove(checkModelAccountExistance);
        restoreState();
    }

    @Override
    protected void filter(String filter) {
        super.filter(filter + " " + sites.get(0).getName());
    }

    @Override
    protected void pauseAll(ActionEvent evt) {
        boolean yes = Dialogs.showConfirmDialog("Pause all models", "", "Pause the recording of all models in this table?", getTabPane().getScene());
        if (yes) {
            new PauseAction(getTabPane(), getFilteredModelsForTab(), recorder).execute();
        }
    }

    @Override
    protected void resumeAll(ActionEvent evt) {
        boolean yes = Dialogs.showConfirmDialog("Resume all models", "", "Pause the recording of all models in this table?", getTabPane().getScene());
        if (yes) {
            new ResumeAction(getTabPane(), getFilteredModelsForTab(), recorder).execute();
        }
    }

    @Override
    protected List<Model> getExportList() {
        return getFilteredModelsForTab();
    }

    private List<Model> getFilteredModelsForTab() {
        return recorder.getModels().stream()
                .filter(Predicate.not(Model::isMarkedForLaterRecording))
                .filter(m -> Objects.equals(m.getSite(), sites.get(0)))
                .toList();
    }
}
