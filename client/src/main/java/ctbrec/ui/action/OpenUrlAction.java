package ctbrec.ui.action;

import java.util.List;

import ctbrec.Model;
import ctbrec.ui.DesktopIntegration;
import javafx.scene.Node;

public class OpenUrlAction extends ModelMassEditAction {

    public OpenUrlAction(Node source, List<? extends Model> models) {
        super(source, models);
        action = m -> DesktopIntegration.open(m.getUrl());
    }
}
