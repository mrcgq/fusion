package ctbrec.recorder.download.hls;

import ctbrec.Settings;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.SplittingStrategy;

public class CombinedSplittingStrategy implements SplittingStrategy {

    private SplittingStrategy[] splittingStrategies;

    public CombinedSplittingStrategy(SplittingStrategy... splittingStrategies) {
        this.splittingStrategies = splittingStrategies;
    }

    @Override
    public void init(Settings settings) {
        for (SplittingStrategy splittingStrategy : splittingStrategies) {
            splittingStrategy.init(settings);
        }
    }

    @Override
    public boolean splitNecessary(RecordingProcess download) {
        for (SplittingStrategy splittingStrategy : splittingStrategies) {
            if (splittingStrategy.splitNecessary(download)) {
                return true;
            }
        }
        return false;
    }
}
