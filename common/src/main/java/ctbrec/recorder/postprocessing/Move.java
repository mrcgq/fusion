package ctbrec.recorder.postprocessing;

import ctbrec.Recording;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Objects;

import static ctbrec.io.IoUtils.deleteEmptyParents;

public class Move extends AbstractPlaceholderAwarePostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Move.class);
    public static final String PATH_TEMPLATE = "path.template";
    public static final String DEFAULT = "$sanitize(${modelName})" + File.separatorChar + "$format(${localDateTime})";

    @Override
    public String getName() {
        return "move";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException {
        Recording rec = ctx.getRecording();
        String pathTemplate = getConfig().getOrDefault(PATH_TEMPLATE, DEFAULT);
        String path = fillInPlaceHolders(pathTemplate, ctx);
        File src = rec.getPostProcessedFile();
        boolean isFile = src.isFile();
        File target = new File(path, src.getName());
        if (Objects.equals(src, target)) {
            return true;
        }
        LOG.info("Moving {} to {}", src.getName(), target.getParentFile().getCanonicalPath());
        Files.createDirectories(target.getParentFile().toPath());
        if (rec.getPostProcessedFile().isDirectory()) {
            FileUtils.moveDirectory(rec.getPostProcessedFile(), target);
        } else {
            FileUtils.moveFile(rec.getPostProcessedFile(), target);
        }
        rec.setPostProcessedFile(target);
        if (Objects.equals(src, rec.getAbsoluteFile())) {
            rec.setAbsoluteFile(target);
        }
        rec.getAssociatedFiles().remove(src.getCanonicalPath());
        rec.getAssociatedFiles().add(target.getCanonicalPath());
        if (isFile) {
            deleteEmptyParents(src.getParentFile());
        } else {
            deleteEmptyParents(src);
        }
        return true;
    }

    @Override
    public String toString() {
        String s = getName();
        if (getConfig().containsKey(PATH_TEMPLATE)) {
            s += " [" + getConfig().get(PATH_TEMPLATE) + ']';
        }
        return s;
    }
}
