package ctbrec.recorder.postprocessing;

import static ctbrec.io.IoUtils.*;

import java.io.IOException;
import java.nio.file.Files;

import ctbrec.Recording;

public class DeleteOriginal extends AbstractPostProcessor {

    @Override
    public String getName() {
        return "delete original";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        Recording rec = ctx.getRecording();
        if (rec.getAbsoluteFile().isFile()) {
            Files.deleteIfExists(rec.getAbsoluteFile().toPath());
            deleteEmptyParents(rec.getAbsoluteFile().getParentFile());
        } else {
            deleteDirectory(rec.getAbsoluteFile());
            deleteEmptyParents(rec.getAbsoluteFile());
        }
        rec.setAbsoluteFile(rec.getPostProcessedFile());
        rec.getAssociatedFiles().remove(rec.getAbsoluteFile().getCanonicalPath());
        return true;
    }
}
