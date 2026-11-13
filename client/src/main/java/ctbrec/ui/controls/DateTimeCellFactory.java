package ctbrec.ui.controls;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class DateTimeCellFactory<T> implements Callback<TableColumn<T, Instant>, TableCell<T, Instant>> {

    private final DateTimeFormatter formatter;

    public DateTimeCellFactory (DateTimeFormatter formatter) {
        this.formatter = formatter;
    }

    @Override
    public TableCell<T, Instant> call(TableColumn<T, Instant> param) {
        return new TableCell<T, Instant>() {
            @Override
            protected void updateItem(Instant item, boolean empty) {
                if (empty || item == null) {
                    setText("");
                } else {
                    var dateTime = LocalDateTime.ofInstant(item, ZoneId.systemDefault());
                    String formattedDateTime = formatter.format(dateTime);
                    setText(item.equals(Instant.EPOCH) ? "" : formattedDateTime);
                }
            }
        };
    }
}
