package ctbrec.recorder.postprocessing;

import static java.lang.Boolean.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.text.MessageFormat;
import java.text.NumberFormat;
import java.util.Arrays;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.OS;
import ctbrec.Recording;
import ctbrec.io.IoUtils;
import ctbrec.recorder.FFmpeg;
import ctbrec.recorder.download.ProcessExitedUncleanException;

public class CreateContactSheet extends AbstractPlaceholderAwarePostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(CreateContactSheet.class);

    public static final String TOTAL_SIZE = "total_size";
    public static final String PADDING = "padding";
    public static final String COLS = "columns";
    public static final String ROWS = "rows";
    public static final String BACKGROUND = "background";
    public static final String FILENAME = "filename";
    public static final String BURN_IN_TIMESTAMP = "burn_in_timestamp";


    @Override
    public String getName() {
        return "create contact sheet";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        Recording rec = ctx.getRecording();
        Config config = ctx.getConfig();
        int totalWidth = Integer.parseInt(getConfig().getOrDefault(TOTAL_SIZE, "1920"));
        int padding = Integer.parseInt(getConfig().getOrDefault(PADDING, "4"));
        int cols = Integer.parseInt(getConfig().getOrDefault(COLS, "8"));
        int rows = Integer.parseInt(getConfig().getOrDefault(ROWS, "7"));
        String color = getConfig().getOrDefault(BACKGROUND, "0x333333");
        String filename = getConfig().getOrDefault(FILENAME, "contactsheet.jpg");
        int thumbWidth = (int) ((totalWidth - (cols + 1) * padding) / (double)cols);

        Path tempDir = createThumbnails(rec, config);

        MessageFormat mf = new MessageFormat("scale={3}:-1,tile={0}x{1}:color={5}:margin={2}:padding={2},scale={4}:-1", Locale.ENGLISH);
        String filterArg = mf.format(new Object[] {
                cols, rows,
                padding,
                Integer.toString(thumbWidth),
                Integer.toString(totalWidth),
                color},
                new StringBuffer(), null).toString();
        File executionDir = rec.getPostProcessedFile().isDirectory() ? rec.getPostProcessedFile() : rec.getPostProcessedFile().getParentFile();
        File output = getOutputFile(executionDir, filename, ctx);
        if (!output.getParentFile().exists()) {
            Files.createDirectories(output.toPath().getParent());
        }

        String[] args = {
                "-y",
                "-i",
                tempDir.toAbsolutePath().toString() + "/image%03d.png",
                "-vf",
                filterArg,
                "-qscale:v",
                "3",
                output.getCanonicalPath()
        };
        String[] cmdline = OS.getFFmpegCommand(args);
        LOG.trace("Executing {} in working directory {}", Arrays.toString(cmdline), executionDir);
        File ffmpegLog = new File(System.getProperty("java.io.tmpdir"), "create_contact_sheet_" + rec.getId() + ".log");
        FFmpeg ffmpeg = new FFmpeg.Builder()
                .logOutput(config.getSettings().logFFmpegOutput)
                .logFile(ffmpegLog)
                .build();
        ffmpeg.exec(cmdline, OS.getEnvironment(), executionDir);
        int exitCode = ffmpeg.waitFor();
        if (exitCode != 0) {
            throw new ProcessExitedUncleanException("FFmpeg exited unclean (" + exitCode + ") when stitching together the contactsheet");
        }
        rec.getAssociatedFiles().add(output.getCanonicalPath());
        IoUtils.deleteDirectory(tempDir.toFile());
        return true;
    }

    private File getOutputFile(File executionDir, String filename, PostProcessingContext ctx) {
        String finalFilename = fillInPlaceHolders(filename, ctx);
        File output;
        if (new File(finalFilename).isAbsolute()) {
            output = new File(finalFilename);
        } else {
            output = new File(executionDir, finalFilename);
        }
        return output;
    }

    private Path createThumbnails(Recording rec, Config config) throws IOException, InterruptedException {
        Path tempDirectory = Files.createTempDirectory("ctbrec-contactsheet-" + rec.getModel().getSanitizedNamed());
        File executionDir = rec.getPostProcessedFile().isDirectory() ? rec.getPostProcessedFile() : rec.getPostProcessedFile().getParentFile();
        int cols = Integer.parseInt(getConfig().getOrDefault(COLS, "8"));
        int rows = Integer.parseInt(getConfig().getOrDefault(ROWS, "7"));
        int numberOfThumbs = rows * cols;
        long lengthInSeconds = rec.getLength().getSeconds();
        double thumbnailInterval = lengthInSeconds / (double)numberOfThumbs;
        String timestampLook = config.getSettings().contactsheetTimestampLook;

        NumberFormat nf = new DecimalFormat("000");
        for (int i = 0; i < numberOfThumbs; i++) {
            double startTimeInSeconds = thumbnailInterval * i;
            String tile = "image" + nf.format(i) + ".png";
            String videoFilter = "scale=-1:720,select='eq(pict_type\\,I)'";
            boolean includeTimestamp = getConfig().getOrDefault(BURN_IN_TIMESTAMP, TRUE.toString()).equals(TRUE.toString());
            if (includeTimestamp) {
                int hours = (int) (startTimeInSeconds / 3600);
                int minutes = (int) (startTimeInSeconds % 3600 / 60);
                int seconds = (int) (startTimeInSeconds % 60);
                String timestamp = String.format("%02d\\:%02d\\:%02d", hours, minutes, seconds);
                videoFilter += ",drawtext='text=" + timestamp + ":" + timestampLook + ":x=(w-text_w-20):y=(h-text_h-20)'";
            }

            File input = getInputFile(rec);
            File output = new File(tempDirectory.toFile(), tile);
            String[] args = {
                    "-y",
                    "-skip_frame",
                    "nokey",
                    "-ss",
                    Double.toString(startTimeInSeconds),
                    "-i",
                    input.getCanonicalPath(),
                    "-vf",
                    videoFilter,
                    "-vframes",
                    "1",
                    output.getCanonicalPath()
            };
            String[] cmdline = OS.getFFmpegCommand(args);
            LOG.trace("Executing {} in working directory {}", Arrays.toString(cmdline), executionDir);

            FFmpeg ffmpeg = new FFmpeg.Builder()
                    .logOutput(config.getSettings().logFFmpegOutput)
                    .logFile(new File(output.getParentFile(), output.getName() + ".log"))
                    .build();
            ffmpeg.exec(cmdline, OS.getEnvironment(), executionDir);
            int exitCode = ffmpeg.waitFor();
            if (exitCode != 0) {
                throw new ProcessExitedUncleanException("FFmpeg exited unclean (" + exitCode + ") for thumbnail " + i + ' ' + Arrays.toString(cmdline));
            }
        }
        return tempDirectory;
    }

    private File getInputFile(Recording rec) {
        if (rec.isSingleFile()) {
            return rec.getPostProcessedFile();
        } else {
            // TODO this has to be changed to support other playlist types like DASH
            return new File(rec.getPostProcessedFile(), "playlist.m3u8");
        }
    }
}
