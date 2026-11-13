package ctbrec.recorder;

import ctbrec.Recording;

public class RecordingPinnedException extends RuntimeException {

    public RecordingPinnedException(Recording rec) {
        super("Recording is pinned: " + rec);
    }
}
