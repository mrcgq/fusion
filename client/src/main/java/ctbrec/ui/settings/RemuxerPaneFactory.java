package ctbrec.ui.settings;

import ctbrec.recorder.postprocessing.PostProcessor;
import ctbrec.recorder.postprocessing.Remux;
import ctbrec.ui.settings.api.Category;
import ctbrec.ui.settings.api.Preferences;
import ctbrec.ui.settings.api.Setting;
import javafx.beans.property.SimpleStringProperty;

public class RemuxerPaneFactory extends AbstractPostProcessingPaneFactory {

    @Override
    public Preferences doCreatePostProcessorPane(PostProcessor pp) {
        var ffmpegParams = new SimpleStringProperty(null, Remux.FFMPEG_ARGS, pp.getConfig().getOrDefault(Remux.FFMPEG_ARGS, "-c:v copy -c:a copy -movflags faststart -y -f mp4"));
        var fileExt = new SimpleStringProperty(null, Remux.FILE_EXT, pp.getConfig().getOrDefault(Remux.FILE_EXT, "mp4"));
        properties.add(ffmpegParams);
        properties.add(fileExt);

        return Preferences.of(new MapPreferencesStorage(),
                Category.of(pp.getName(),
                        Setting.of("FFmpeg parameters", ffmpegParams),
                        Setting.of("File extension", fileExt)
                        )
                );
    }

}
