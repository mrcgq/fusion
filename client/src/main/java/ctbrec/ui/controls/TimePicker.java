package ctbrec.ui.controls;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.ScrollEvent;

public class TimePicker extends Spinner<LocalTime> {

    public TimePicker() {
        this(LocalTime.now().truncatedTo(ChronoUnit.MINUTES));
    }

    public TimePicker(LocalTime value) {
        setValueFactory(new TimePickerValueFactory());
        getValueFactory().setValue(value);
        setEditable(true);
        getEditor().setOnKeyReleased(this::updateValueFromInput);
        getEditor().focusedProperty().addListener((obs, oldV, focused) -> {
            if (Boolean.FALSE.equals(focused)) {
                getEditor().setText(getValue().toString());
            }
        });
        setOnScroll(this::onScroll);
    }

    private void onScroll(ScrollEvent evt) {
        int d = (int) evt.getDeltaY();
        int units = (int) (Math.abs(d) / evt.getMultiplierY());
        if (d > 0) {
            getValueFactory().increment(units);
        } else {
            getValueFactory().decrement(units);
        }
        evt.consume();
    }

    private void updateValueFromInput(KeyEvent evt) {
        String input = getEditor().getText();
        try {
            LocalTime newValue = LocalTime.parse(input);
            getValueFactory().setValue(newValue);
        } catch (DateTimeParseException e) {
            // input is invalid, we do nothing
        }
    }

    private class TimePickerValueFactory extends SpinnerValueFactory<LocalTime> {
        @Override
        public void decrement(int steps) {
            LocalTime time = Optional.ofNullable(getValue()).orElse(LocalTime.now().truncatedTo(ChronoUnit.MINUTES));
            setValue(time.minusMinutes(steps));

        }

        @Override
        public void increment(int steps) {
            LocalTime time = Optional.ofNullable(getValue()).orElse(LocalTime.now().truncatedTo(ChronoUnit.MINUTES));
            setValue(time.plusMinutes(steps));
        }
    }
}
