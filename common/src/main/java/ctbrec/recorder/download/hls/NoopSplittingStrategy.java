package ctbrec.recorder.download.hls;

import ctbrec.Settings;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.SplittingStrategy;

public class NoopSplittingStrategy implements SplittingStrategy {

    @Override
    public void init(Settings settings) {
        // settings not needed
    }

    @Override
    public boolean splitNecessary(RecordingProcess download) {
        return false;
    }

}
