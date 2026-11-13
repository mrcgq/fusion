package ctbrec.recorder.download.hls;

import ctbrec.Settings;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.SplittingStrategy;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class TimeSplittingStrategy implements SplittingStrategy {

    private Settings settings;

    @Override
    public void init(Settings settings) {
        this.settings = settings;
    }

    @Override
    public boolean splitNecessary(RecordingProcess download) {
        ZonedDateTime startTime = download.getStartTime().atZone(ZoneId.systemDefault());
        Duration recordingDuration = Duration.between(startTime, ZonedDateTime.now());
        long seconds = recordingDuration.getSeconds();
        return seconds >= settings.splitRecordingsAfterSecs;
    }

}
