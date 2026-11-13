package ctbrec.recorder.download;

import ctbrec.Settings;

public interface SplittingStrategy {

    void init(Settings settings);

    boolean splitNecessary(RecordingProcess download);
}
