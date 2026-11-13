package ctbrec.recorder.postprocessing;

import ctbrec.Config;
import ctbrec.Recording;
import ctbrec.notes.ModelNotesService;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.RecordingManager;
import lombok.Data;

@Data
public class PostProcessingContext {

    private Recorder recorder;
    private Recording recording;
    private RecordingManager recordingManager;
    private Config config;
    private ModelNotesService modelNotesService;
}
