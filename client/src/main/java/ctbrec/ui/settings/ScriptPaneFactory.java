package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.recorder.postprocessing.Script;
import ctbrec.ui.settings.api.Category;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.Setting;
import javafx.beans.property.SimpleStringProperty;

public class ScriptPaneFactory extends AbstractPostProcessingPaneFactory {

    @Override
    public Preferences doCreatePostProcessorPane(PostProcessor pp) {
        var script = new SimpleStringProperty(null, Script.SCRIPT_EXECUTABLE, pp.getConfig().getOrDefault(Script.SCRIPT_EXECUTABLE, "c:\\users\\johndoe\\somescript"));
        var params = new SimpleStringProperty(null, Script.SCRIPT_PARAMS, pp.getConfig().getOrDefault(Script.SCRIPT_PARAMS, "${absolutePath}"));
        properties.add(script);
        properties.add(params);

        return Preferences.of(new MapPreferencesStorage(),
                Category.of(pp.getName(),
                        Setting.of("Script", script),
                        Setting.of("Parameters", params)
                        )
                );
    }

}
