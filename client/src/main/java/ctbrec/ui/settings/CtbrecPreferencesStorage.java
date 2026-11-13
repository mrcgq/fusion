package ctbrec.ui.settings;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.Settings;
import ctbrec.StringUtil;
import ctbrec.ui.controls.DirectorySelectionBox;
import ctbrec.ui.controls.ProgramSelectionBox;
import ctbrec.ui.controls.TimePicker;
import ctbrec.ui.controls.range.DiscreteRange;
import ctbrec.ui.controls.range.RangeSlider;
import ctbrec.ui.settings.api.ExclusiveSelectionProperty;
import ctbrec.ui.settings.api.LocalTimeProperty;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.PreferencesStorage;
import ctbrec.ui.settings.api.Setting;
import ctbrec.ui.settings.api.SimpleDirectoryProperty;
import ctbrec.ui.settings.api.SimpleFileProperty;
import ctbrec.ui.settings.api.SimpleJoinedStringListProperty;
import ctbrec.ui.settings.api.SimpleRangeProperty;
import ctbrec.io.BoundField;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ListProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.Property;
import javafx.beans.property.StringProperty;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.util.converter.NumberStringConverter;

public class CtbrecPreferencesStorage implements PreferencesStorage {

    private static final Logger LOG = LoggerFactory.getLogger(CtbrecPreferencesStorage.class);
    public static final String PATTERN_NOT_A_DIGIT = "[^\\d]";
    public static final String COULDNT_SAVE_MSG = "Couldn't save config setting";

    private Config config;
    private Settings settings;
    private Preferences prefs;

    public CtbrecPreferencesStorage(Config config) {
        this.config = config;
        this.settings = config.getSettings();
    }

    public void setPreferences(Preferences prefs) {
        this.prefs = prefs;
    }

    @Override
    public void save(Preferences preferences) throws IOException {
        throw new RuntimeException("not implemented");
    }

    @Override
    public void load(Preferences preferences) {
        throw new RuntimeException("not implemented");
    }

    @Override
    public Node createGui(Setting setting) throws NoSuchFieldException, IllegalAccessException {
        config.disableSaving();
        try {
            Property<?> prop = setting.getProperty();
            if (prop instanceof ExclusiveSelectionProperty) {
                return createRadioGroup(setting);
            } else if (prop instanceof SimpleRangeProperty) {
                return createRangeSlider(setting);
            } else if (prop instanceof SimpleDirectoryProperty) {
                return createDirectorySelector(setting);
            } else if (prop instanceof SimpleFileProperty) {
                return createFileSelector(setting);
            } else if (prop instanceof LocalTimeProperty) {
                return createTimeSelector(setting);
            } else if (prop instanceof IntegerProperty) {
                return createIntegerProperty(setting);
            } else if (prop instanceof LongProperty) {
                return createLongProperty(setting);
            } else if (prop instanceof BooleanProperty) {
                return createBooleanProperty(setting);
            } else if (prop instanceof ListProperty) {
                return createComboBox(setting);
            } else if (prop instanceof SimpleJoinedStringListProperty) {
                return createStringListProperty(setting);
            } else if (prop instanceof StringProperty) {
                return createStringProperty(setting);
            } else {
                return new Label("Unsupported Type for key " + setting.getKey() + ": " + setting.getProperty());
            }
        } finally {
            config.enableSaving();
        }
    }

    private Node createRadioGroup(Setting setting) {
        ExclusiveSelectionProperty prop = (ExclusiveSelectionProperty) setting.getProperty();
        var toggleGroup = new ToggleGroup();
        var optionA = new RadioButton(prop.getOptionA());
        optionA.setSelected(prop.getValue());
        optionA.setToggleGroup(toggleGroup);
        var optionB = new RadioButton(prop.getOptionB());
        optionB.setSelected(!optionA.isSelected());
        optionB.setToggleGroup(toggleGroup);
        optionA.selectedProperty().bindBidirectional(prop);
        prop.addListener((obs, oldV, newV) -> saveValue(() -> {            
            if (setIfChanged(setting.getKey(), newV)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        var row = new HBox();
        row.getChildren().addAll(optionA, optionB);
        HBox.setMargin(optionA, new Insets(5));
        HBox.setMargin(optionB, new Insets(5));
        return row;
    }

    private void runRestartRequiredCallback() {
        Optional.ofNullable(prefs).map(Preferences::getRestartRequiredCallback).ifPresent(r -> {
            try {
                r.run();
            } catch (RuntimeException e) {
                LOG.warn("Error while calling \"restart required\" callback", e);
            }
        });
    }

    @SuppressWarnings("unchecked")
    private Node createRangeSlider(Setting setting) {
        SimpleRangeProperty<Integer> rangeProperty = (SimpleRangeProperty<Integer>) setting.getProperty();
        DiscreteRange<Integer> range = (DiscreteRange<Integer>) rangeProperty.getRange();
        List<Integer> labels = (List<Integer>) range.getLabels();
        List<Integer> values = range.getTicks();
        RangeSlider<Integer> resolutionRange = new RangeSlider<>(rangeProperty.getRange());
        resolutionRange.setShowTickMarks(true);
        resolutionRange.setShowTickLabels(true);
        int lowValue = getRangeSliderValue(values, labels, Config.getInstance().getSettings().minimumResolution);
        resolutionRange.setLow(lowValue >= 0 ? lowValue : values.get(0));
        int highValue = getRangeSliderValue(values, labels, Config.getInstance().getSettings().maximumResolution);
        resolutionRange.setHigh(highValue >= 0 ? highValue : values.get(values.size() - 1));
        resolutionRange.getLow().addListener((obs, o, n) -> saveValue(() -> {
            int newV = labels.get(n.intValue());
            if (setIfChanged(rangeProperty.getLowKey(), newV)) {
                config.save();
            }
        }));
        resolutionRange.getHigh().addListener((obs, o, n) -> saveValue(() -> {
            int newV = labels.get(n.intValue());
            if (setIfChanged(rangeProperty.getHighKey(), newV)) {
                config.save();
            }
        }));
        return resolutionRange;
    }

    private int getRangeSliderValue(List<Integer> values, List<Integer> labels, int value) {
        for (var i = 0; i < labels.size(); i++) {
            var label = labels.get(i).intValue();
            if (label == value) {
                return values.get(i);
            }
        }
        return -1;
    }

    private Node createFileSelector(Setting setting) {
        var programSelector = new ProgramSelectionBox("");
        programSelector.fileProperty().addListener((obs, o, n) -> saveValue(() -> {
            if (setIfChanged(setting.getKey(), n)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        StringProperty property = (StringProperty) setting.getProperty();
        programSelector.fileProperty().bindBidirectional(property);
        return programSelector;
    }

    private Node createDirectorySelector(Setting setting) {
        var directorySelector = new DirectorySelectionBox("");
        directorySelector.prefWidth(400);
        directorySelector.fileProperty().addListener((obs, o, n) -> saveValue(() -> {
            if (setIfChanged(setting.getKey(), n)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        StringProperty property = (StringProperty) setting.getProperty();
        directorySelector.fileProperty().bindBidirectional(property);
        return directorySelector;
    }

    private Node createTimeSelector(Setting setting) {
        LocalTime time = (LocalTime) setting.getProperty().getValue();
        var timePicker = new TimePicker(time);
        timePicker.valueProperty().addListener((obs, o, n) -> saveValue(() -> {
            if (setIfChanged(setting.getKey(), n)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        return timePicker;
    }

    private Node createStringProperty(Setting setting) {
        var ctrl = new TextField();
        ctrl.textProperty().addListener((obs, oldV, newV) -> saveValue(() -> {
            if (setIfChanged(setting.getKey(), newV)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        StringProperty prop = (StringProperty) setting.getProperty();
        ctrl.textProperty().bindBidirectional(prop);
        return ctrl;
    }

    private Node createStringListProperty(Setting setting) {
        var ctrl = new TextArea();
        StringProperty prop = (StringProperty) setting.getProperty();
        ctrl.textProperty().bindBidirectional(prop);
        prop.addListener((obs, oldV, newV) -> saveValue(() -> {
            //setUnchecked(setting.getKey(), Arrays.asList(newV.split("\n")));
            if (setting.doesNeedRestart()) {
                runRestartRequiredCallback();
            }
            config.save();
        }));
        return ctrl;
    }

    @SuppressWarnings("unchecked")
    private Node createIntegerProperty(Setting setting) {
        var ctrl = new TextField();
        ctrl.textProperty().addListener((obs, oldV, newV) -> saveValue(() -> {
            if (!newV.matches("\\d*")) {
                ctrl.setText(newV.replaceAll(PATTERN_NOT_A_DIGIT, ""));
            }
            if (!ctrl.getText().isEmpty() && setIfChanged(setting.getKey(), Integer.parseInt(ctrl.getText()))) {
                if (setting.doesNeedRestart() && prefs != null) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        Property<Number> prop = setting.getProperty();
        ctrl.textProperty().bindBidirectional(prop, new NumberStringConverter());
        return ctrl;
    }

    @SuppressWarnings("unchecked")
    private Node createLongProperty(Setting setting) {
        var ctrl = new TextField();
        ctrl.textProperty().addListener((obs, oldV, newV) -> saveValue(() -> {
            if (!newV.matches("\\d*")) {
                ctrl.setText(newV.replaceAll(PATTERN_NOT_A_DIGIT, ""));
            }
            if (!ctrl.getText().isEmpty()) {
                var value = Long.parseLong(ctrl.getText());
                if (setting.getConverter() != null) {
                    value = (long) setting.getConverter().convertFrom(value);
                }
                if (setIfChanged(setting.getKey(), value)) {
                    if (setting.doesNeedRestart() && !Objects.equals(oldV, newV)) {
                        runRestartRequiredCallback();
                    }
                    config.save();
                }
            }
        }));
        Property<Number> prop = setting.getProperty();
        ctrl.textProperty().bindBidirectional(prop, new NumberStringConverter());
        return ctrl;
    }

    private Node createBooleanProperty(Setting setting) {
        var ctrl = new CheckBox();
        ctrl.selectedProperty().addListener((obs, oldV, newV) -> saveValue(() -> {
            if (setIfChanged(setting.getKey(), newV)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        BooleanProperty prop = (BooleanProperty) setting.getProperty();
        ctrl.selectedProperty().bindBidirectional(prop);
        return ctrl;
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    private Node createComboBox(Setting setting) throws IllegalAccessException, NoSuchFieldException  {
        ListProperty<?> listProp = (ListProperty<?>) setting.getProperty();
        ComboBox<Object> comboBox = new ComboBox(listProp);
        Object value = BoundField.of(settings, setting.getKey()).get();
        if (StringUtil.isNotBlank(value.toString())) {
            if (setting.getConverter() != null) {
                comboBox.getSelectionModel().select(setting.getConverter().convertTo(value));
            } else {
                comboBox.getSelectionModel().select(value);
            }
        }
        comboBox.valueProperty().addListener((obs, oldV, newV) -> saveValue(() -> {
            LOG.debug("Saving setting {}", setting.getKey());
            if (setIfChanged(setting.getKey(), setting.getConverter() != null ? setting.getConverter().convertFrom(newV) : newV)) {
                if (setting.doesNeedRestart()) {
                    runRestartRequiredCallback();
                }
                config.save();
            }
        }));
        if (setting.getChangeListener() != null) {
            comboBox.valueProperty().addListener((ChangeListener<? super Object>) setting.getChangeListener());
        }
        return comboBox;
    }
    
    
    private boolean setIfChanged(String key, Object n) throws IllegalAccessException, NoSuchFieldException, InvocationTargetException {        
        var field = BoundField.of(settings, key);
        var o = field.get();
        if (!Objects.equals(n, o)) {
            if (n instanceof List && o instanceof List) {
                var list = (List<String>)o;
                list.clear();
                list.addAll((List<String>)n);
            } else {
                field.set(n); // NOSONAR
            }
            return true;
        }
        return false;
    }
    
    private boolean setUnchecked(String key, Object n) throws IllegalAccessException, NoSuchFieldException, InvocationTargetException {        
        var field = BoundField.of(settings, key);
        var o = field.get();
        if (n instanceof List && o instanceof List) {
            var list = (List<String>)o;
            list.clear();
            list.addAll((List<String>)n);
        } else {
            field.set(n); // NOSONAR
        }
        return true;
    }

    private void saveValue(Exec exe) {
        try {
            exe.run();
        } catch (Exception e) {
            LOG.error(COULDNT_SAVE_MSG, e);
        }
    }

    @FunctionalInterface
    private interface Exec {
        public void run() throws IllegalAccessException, IOException, NoSuchFieldException, NoSuchMethodException, InvocationTargetException;
    }
}
