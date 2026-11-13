package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.recorder.postprocessing.Rename;
import ctbrec.ui.settings.api.Category;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.Setting;
import javafx.beans.property.SimpleStringProperty;

public class RenamerPaneFactory extends AbstractPostProcessingPaneFactory {

    @Override
    public Preferences doCreatePostProcessorPane(PostProcessor pp) {
        var fileTemplate = new SimpleStringProperty(null, Rename.FILE_NAME_TEMPLATE, pp.getConfig().getOrDefault(Rename.FILE_NAME_TEMPLATE, Rename.DEFAULT));
        properties.add(fileTemplate);

        return Preferences.of(new MapPreferencesStorage(),
                Category.of(pp.getName(),
                        Setting.of("File name", fileTemplate)
                        )
                );
    }

}
