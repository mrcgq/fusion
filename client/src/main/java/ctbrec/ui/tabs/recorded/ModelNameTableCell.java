package ctbrec.ui.tabs.recorded;

import static ctbrec.ui.Icon.*;

import java.util.Map;
import java.util.stream.Collectors;

import ctbrec.Model;
import ctbrec.recorder.Recorder;
import javafx.scene.image.ImageView;

public class ModelNameTableCell extends IconTableCell<ModelName> {

    private Recorder recorder;

    public ModelNameTableCell(Recorder recorder) {
        super(Map.of( //
                GROUP_16, new ImageView(GROUP_16.url()), //
                BLANK_16, new ImageView(BLANK_16.url())));
        this.recorder = recorder;
    }

    @Override
    protected void updateItem(ModelName modelName, boolean empty) {
        setText(null);
        tooltip = null;
        show(BLANK_16);
        hide(GROUP_16);

        if (modelName != null && !empty) {
            setText(modelName.toString());
            Model m = getTableView().getItems().get(getTableRow().getIndex());
            recorder.getModelGroup(m).ifPresent(group -> {
                hide(BLANK_16);
                show(GROUP_16);
                tooltip = group.getModelUrls().size() + " models:\n";
                tooltip += group.getModelUrls().stream().collect(Collectors.joining("\n"));
            });
            if (m.isForcePriority()) {
            	this.setStyle(getStyle() + "-fx-text-fill: darkred;" + "-fx-font-weight: bold;");
            }
        }
        super.updateItem(modelName, empty);
    }
}
