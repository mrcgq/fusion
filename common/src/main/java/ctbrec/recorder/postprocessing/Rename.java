package ctbrec.recorder.postprocessing;

import ctbrec.Recording;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

public class Rename extends AbstractPlaceholderAwarePostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Rename.class);
    public static final String FILE_NAME_TEMPLATE = "filename.template";
    public static final String DEFAULT = "$sanitize(${modelName})_$format(${localDateTime}).${fileSuffix}";
    public static final String DEFAULT_DIR = "$sanitize(${modelName})_$format(${localDateTime})";

    @Override
    public String getName() {
        return "rename";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException {
        Recording rec = ctx.getRecording();
        String defaultTemplate = rec.isSingleFile() ? DEFAULT : DEFAULT_DIR;
        String filenameTemplate = getConfig().getOrDefault(FILE_NAME_TEMPLATE, defaultTemplate);
        String filename = fillInPlaceHolders(filenameTemplate, ctx);
        File src = rec.getPostProcessedFile();
        File target = new File(src.getParentFile(), filename);
        if (Objects.equals(src, target)) {
            return true;
        }
        LOG.info("Renaming {} to {}", src.getName(), target.getName());
        Files.move(rec.getPostProcessedFile().toPath(), target.toPath());
        rec.setPostProcessedFile(target);
        if (Objects.equals(src, rec.getAbsoluteFile())) {
            rec.setAbsoluteFile(target);
        }
        rec.getAssociatedFiles().remove(src.getCanonicalPath());
        rec.getAssociatedFiles().add(target.getCanonicalPath());
        return true;
    }

    @Override
    public String toString() {
        String s = getName();
        if (getConfig().containsKey(FILE_NAME_TEMPLATE)) {
            s += " [" + getConfig().get(FILE_NAME_TEMPLATE) + ']';
        }
        return s;
    }
}
