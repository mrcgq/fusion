package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.ui.controls.DirectorySelectionBox;
import ctbrec.ui.controls.ProgramSelectionBox;
import ctbrec.ui.settings.api.*;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.util.converter.NumberStringConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public abstract class AbstractPostProcessingPaneFactory {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractPostProcessingPaneFactory.class);
    private PostProcessor pp;
    Set<Property<?>> properties = new HashSet<>();

    public abstract Preferences doCreatePostProcessorPane(PostProcessor pp);

    public Preferences createPostProcessorPane(PostProcessor pp) {
        this.pp = pp;
        return doCreatePostProcessorPane(pp);
    }

    class MapPreferencesStorage implements PreferencesStorage {
        @Override
        public void save(Preferences preferences) throws IOException {
            for (Property<?> property : properties) {
                String key = property.getName();
                Object value = preferences.getSetting(key).get().getProperty().getValue();
                LOG.debug("{}={}", key, value);
                pp.getConfig().put(key, value.toString());
            }
        }

        @Override
        public void load(Preferences preferences) {
            // no op
        }

        @Override
        public Node createGui(Setting setting) throws NoSuchFieldException, IllegalAccessException {
            Property<?> prop = setting.getProperty();
            if (prop instanceof ExclusiveSelectionProperty) {
                return createRadioGroup(setting);
            } else if (prop instanceof SimpleDirectoryProperty) {
                return createDirectorySelector(setting);
            } else if (prop instanceof SimpleFileProperty) {
                return createFileSelector(setting);
            } else if (prop instanceof IntegerProperty) {
                return createIntegerProperty(setting);
            } else if (prop instanceof LongProperty) {
                return createLongProperty(setting);
            } else if (prop instanceof BooleanProperty) {
                return createBooleanProperty(setting);
            } else if (prop instanceof ListProperty) {
                return createComboBox(setting);
            } else if (prop instanceof StringProperty) {
                return createStringProperty(setting);
            } else {
                return new Label("Unsupported Type for key " + setting.getKey() + ": " + setting.getProperty());
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
            var row = new HBox();
            row.getChildren().addAll(optionA, optionB);
            HBox.setMargin(optionA, new Insets(5));
            HBox.setMargin(optionB, new Insets(5));
            return row;
        }

        private Node createFileSelector(Setting setting) {
            var programSelector = new ProgramSelectionBox("");
            StringProperty property = (StringProperty) setting.getProperty();
            programSelector.fileProperty().bindBidirectional(property);
            return programSelector;
        }

        private Node createDirectorySelector(Setting setting) {
            var directorySelector = new DirectorySelectionBox("");
            directorySelector.prefWidth(400);
            StringProperty property = (StringProperty) setting.getProperty();
            directorySelector.fileProperty().bindBidirectional(property);
            return directorySelector;
        }

        @SuppressWarnings("unchecked")
        private Node createStringProperty(Setting setting) {
            var ctrl = new TextField();
            ctrl.textProperty().bindBidirectional(setting.getProperty());
            return ctrl;
        }

        @SuppressWarnings("unchecked")
        private Node createIntegerProperty(Setting setting) {
            var ctrl = new TextField();
            Property<Number> prop = setting.getProperty();
            ctrl.textProperty().bindBidirectional(prop, new NumberStringConverter());
            return ctrl;
        }

        @SuppressWarnings("unchecked")
        private Node createLongProperty(Setting setting) {
            var ctrl = new TextField();
            Property<Number> prop = setting.getProperty();
            ctrl.textProperty().bindBidirectional(prop, new NumberStringConverter());
            return ctrl;
        }

        private Node createBooleanProperty(Setting setting) {
            var ctrl = new CheckBox();
            BooleanProperty prop = (BooleanProperty) setting.getProperty();
            ctrl.selectedProperty().bindBidirectional(prop);
            return ctrl;
        }

        @SuppressWarnings({ "rawtypes", "unchecked" })
        private Node createComboBox(Setting setting) {
            ListProperty<?> listProp = (ListProperty<?>) setting.getProperty();
            ComboBox<Object> comboBox = new ComboBox(listProp);
            return comboBox;
        }
    }
}
