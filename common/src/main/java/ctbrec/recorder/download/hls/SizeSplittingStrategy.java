package ctbrec.recorder.download.hls;

import ctbrec.Settings;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.SplittingStrategy;

public class SizeSplittingStrategy implements SplittingStrategy {

    private Settings settings;

    @Override
    public void init(Settings settings) {
        this.settings = settings;
    }

    @Override
    public boolean splitNecessary(RecordingProcess download) {
        long sizeInByte = download.getSizeInByte();
        return sizeInByte >= settings.splitRecordingsBiggerThanBytes;
    }

}
