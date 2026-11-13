package ctbrec.io;

import ctbrec.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;

public class IoUtils {

    private static final Logger LOG = LoggerFactory.getLogger(IoUtils.class);

    private IoUtils() {
    }

    public static void deleteEmptyParents(File parent) throws IOException {
        File recDir = new File(Config.getInstance().getSettings().recordingsDir);
        while (parent != null && (parent.list() != null && parent.list().length == 0 || !parent.exists())) {
            if (parent.equals(recDir)) {
                return;
            }
            if (parent.exists()) {
                LOG.debug("Deleting empty directory {}", parent.getAbsolutePath());
                Files.delete(parent.toPath());
            }
            parent = parent.getParentFile();
        }
    }

    public static void deleteDirectory(File directory) throws IOException {
        if (!directory.exists()) {
            return;
        }

        File[] files = directory.listFiles();
        boolean deletedAllFiles = true;
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectory(file);
            } else {
                try {
                    LOG.trace("Deleting {}", file.getAbsolutePath());
                    Files.delete(file.toPath());
                } catch (Exception e) {
                    deletedAllFiles = false;
                    LOG.debug("Couldn't delete {}", file, e);
                }
            }
        }

        if (!deletedAllFiles) {
            throw new IOException("Couldn't delete all files in " + directory);
        }

        Files.delete(directory.toPath());
    }

    public static long getDirectorySize(File dir) {
        final long[] size = {0};
        int maxDepth = 7;
        try {
            Files.walkFileTree(dir.toPath(), EnumSet.noneOf(FileVisitOption.class), maxDepth, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    size[0] += attrs.size();
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    // Ignore file access issues
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            LOG.error("Couldn't determine size of directory {}", dir, e);
        }
        return size[0];
    }

    public static void close(OutputStream outputStream, String errorMsg) {
        if (outputStream != null) {
            try {
                outputStream.close();
            } catch (IOException e) {
                LOG.error(errorMsg, e);
            }
        }
    }
}
