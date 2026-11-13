package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.Move;
import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.ui.settings.api.Category;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.Setting;
import javafx.beans.property.SimpleStringProperty;

public class MoverPaneFactory extends AbstractPostProcessingPaneFactory {

    @Override
    public Preferences doCreatePostProcessorPane(PostProcessor pp) {
        var pathTemplate = new SimpleStringProperty(null, Move.PATH_TEMPLATE, pp.getConfig().getOrDefault(Move.PATH_TEMPLATE, Move.DEFAULT));
        properties.add(pathTemplate);

        return Preferences.of(new MapPreferencesStorage(),
                Category.of(pp.getName(),
                        Setting.of("Directory", pathTemplate)
                        )
                );
    }

}
