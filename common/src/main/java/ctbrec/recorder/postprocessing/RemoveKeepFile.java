package ctbrec.recorder.postprocessing;

import java.io.IOException;

public class RemoveKeepFile extends AbstractPostProcessor {

    @Override
    public String getName() {
        return "remove recording, but keep the files";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        ctx.getRecordingManager().remove(ctx.getRecording());
        ctx.getRecording().setMetaDataFile(null);
        return true;
    }
}
