package ctbrec.recorder.postprocessing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Recording;

public class Copy extends AbstractPostProcessor {

    private static final transient Logger LOG = LoggerFactory.getLogger(Copy.class);

    @Override
    public String getName() {
        return "create a copy";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        Recording rec = ctx.getRecording();
        File orig = rec.getPostProcessedFile();
        String copyFilename = getFilenameForCopy(orig);
        File copy = new File(orig.getParentFile(), copyFilename);
        LOG.info("Creating a copy {}", copy);
        if (orig.isFile()) {
            Files.copy(rec.getPostProcessedFile().toPath(), copy.toPath());
        } else {
            FileUtils.copyDirectory(orig, copy, true);
        }
        rec.setPostProcessedFile(copy);
        rec.getAssociatedFiles().add(copy.getCanonicalPath());
        return true;
    }

    private String getFilenameForCopy(File orig) {
        String filename = orig.getName();
        if (orig.isFile()) {
            String name = filename.substring(0, filename.lastIndexOf('.'));
            String ext = filename.substring(filename.lastIndexOf('.') + 1);
            return name + "_copy." + ext;
        } else {
            return filename + "_copy";
        }
    }
}
