package ctbrec.recorder.postprocessing;

import ctbrec.Recording;
import ctbrec.recorder.RecordingManager;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Duration;

@Slf4j
public class DeleteTooShort extends AbstractPostProcessor {

    public static final String MIN_LEN_IN_SECS = "minimumLengthInSeconds";

    @Override
    public String getName() {
        return "delete too short";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException {
        Recording rec = ctx.getRecording();
        RecordingManager recordingManager = ctx.getRecordingManager();
        Duration minimumLengthInSeconds = Duration.ofSeconds(Integer.parseInt(getConfig().getOrDefault(MIN_LEN_IN_SECS, "0")));
        if (minimumLengthInSeconds.getSeconds() > 0) {
            Duration recordingLength = rec.getLength();
            if (recordingLength.isNegative()) {
                log.info("Video length couldn't be determined. Keeping the file!");
            } else {
                if (!recordingLength.isZero() && recordingLength.compareTo(minimumLengthInSeconds) < 0) {
                    log.info("Deleting too short recording {} [{} < {}]", rec, recordingLength, minimumLengthInSeconds);
                    recordingManager.delete(rec);
                    return false;
                }
            }
        }
        return true;
    }
}
