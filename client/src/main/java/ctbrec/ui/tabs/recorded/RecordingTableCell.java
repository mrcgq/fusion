package ctbrec.ui.tabs.recorded;

import static ctbrec.ui.Icon.*;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Map;
import java.util.Objects;

import ctbrec.Model;
import ctbrec.SubsequentAction;
import javafx.scene.image.ImageView;

public class RecordingTableCell extends IconTableCell<Boolean> {

    public RecordingTableCell() {
        super(Map.of( //
                CHECK_16, new ImageView(CHECK_16.url()), //
                CLOCK_16, new ImageView(CLOCK_16.url())));
    }

    @Override
    protected void updateItem(Boolean value, boolean empty) {
        tooltip = null;
        hide(CHECK_16);
        hide(CLOCK_16);
        if (value == null || empty) {
            return;
        }

        if (Objects.equals(value, Boolean.TRUE)) {
            show(CHECK_16);
            tooltip = "Recording";
        }

        Model m = getTableView().getItems().get(getTableRow().getIndex());
        if (m.isRecordingTimeLimited()) {
            show(CLOCK_16);
            var dtf = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
            var zonedDateTime = m.getRecordUntil().atZone(ZoneId.systemDefault());
            String action = m.getRecordUntilSubsequentAction() == SubsequentAction.PAUSE ? "Pause" : "Remove";
            tooltip = action + " at " + dtf.format(zonedDateTime);
        }
        super.updateItem(value, empty);
    }
}