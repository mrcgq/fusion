package ctbrec.ui.tabs.recorded;

import ctbrec.ui.JavaFxModel;
import ctbrec.ui.action.PlayAction;
import javafx.scene.control.TableCell;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;

public class ClickableTableCell<T> extends TableCell<JavaFxModel, T> {

    public ClickableTableCell() {
        addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                JavaFxModel selectedModel = getTableView().getSelectionModel().getSelectedItem();
                if(selectedModel != null) {
                    new PlayAction(getTableView(), selectedModel).execute();
                }
            }
        });
    }
}
