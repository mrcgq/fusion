package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.DeleteTooShort;
import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.ui.settings.api.Category;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.Setting;
import javafx.beans.property.SimpleStringProperty;

public class DeleteTooShortPaneFactory extends AbstractPostProcessingPaneFactory {

    @Override
    public Preferences doCreatePostProcessorPane(PostProcessor pp) {
        var minimumLengthInSeconds = new SimpleStringProperty(null, DeleteTooShort.MIN_LEN_IN_SECS, pp.getConfig().getOrDefault(DeleteTooShort.MIN_LEN_IN_SECS, "10"));
        properties.add(minimumLengthInSeconds);

        return Preferences.of(new MapPreferencesStorage(),
                Category.of(pp.getName(),
                        Setting.of("Minimum length in seconds", minimumLengthInSeconds)
                        )
                );
    }

}
