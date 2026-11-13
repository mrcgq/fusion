package ctbrec.recorder.postprocessing;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.OS;
import ctbrec.Recording;
import ctbrec.io.IoUtils;
import ctbrec.recorder.FFmpeg;

public class Remux extends AbstractPostProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(Remux.class);

    public static final String FFMPEG_ARGS =  "ffmpeg.args";
    public static final String FILE_EXT =  "file.ext";

    @Override
    public String getName() {
        return "remux / transcode";
    }

    @Override
    public boolean postprocess(PostProcessingContext ctx) throws IOException, InterruptedException {
        Recording rec = ctx.getRecording();
        final File inputFile;
        if (rec.getPostProcessedFile().isDirectory()) {
            inputFile = new File(rec.getPostProcessedFile(), "playlist.m3u8");
        } else {
            inputFile = rec.getPostProcessedFile();
        }
        String fileExt = getConfig().get(FILE_EXT);
        File remuxedFile = new File(rec.getPostProcessedFile().getAbsolutePath() + '.' + fileExt);
        String[] cmdline = prepareCommandline(inputFile, remuxedFile);
        File executionDir = rec.getPostProcessedFile().isDirectory() ? rec.getPostProcessedFile() : rec.getPostProcessedFile().getParentFile();
        LOG.info("Executing {} in working directory {}", Arrays.toString(cmdline), executionDir);

        File ffmpegLog = new File(System.getProperty("java.io.tmpdir"), "remux_" + rec.getId() + ".log");
        FFmpeg ffmpeg = new FFmpeg.Builder()
                .logOutput(ctx.getConfig().getSettings().logFFmpegOutput)
                .logFile(ffmpegLog)
                .onExit(exitCode -> finalizeStep(exitCode, rec, inputFile, remuxedFile))
                .build();
        ffmpeg.exec(cmdline, new String[0], executionDir);
        int exitCode = ffmpeg.waitFor();
        return exitCode != 1;
    }

    private void finalizeStep(int exitCode, Recording rec, File inputFile, File remuxedFile) {
        if (exitCode != 1) {
            try {
                rec.setPostProcessedFile(remuxedFile);
                if (inputFile.getName().equals("playlist.m3u8")) {
                    IoUtils.deleteDirectory(inputFile.getParentFile());
                    if (Objects.equals(inputFile.getParentFile(), rec.getAbsoluteFile())) {
                        rec.setAbsoluteFile(remuxedFile);
                    }
                } else {
                    Files.deleteIfExists(inputFile.toPath());
                    if (Objects.equals(inputFile, rec.getAbsoluteFile())) {
                        rec.setAbsoluteFile(remuxedFile);
                    }
                }
                rec.setSingleFile(true);
                rec.setSizeInByte(remuxedFile.length());
                IoUtils.deleteEmptyParents(inputFile.getParentFile());
                rec.getAssociatedFiles().remove(inputFile.getCanonicalPath());
                rec.getAssociatedFiles().add(remuxedFile.getCanonicalPath());
            } catch (IOException e) {
                LOG.error("Couldn't finalize remux post-processing step", e);
            }
        }
    }

    private String[] prepareCommandline(File inputFile, File remuxedFile) throws IOException {
        String[] args = getConfig().get(FFMPEG_ARGS).split(" ");
        String[] argsPlusFile = new String[args.length + 3];
        int i = 0;
        argsPlusFile[i++] = "-i";
        argsPlusFile[i++] = inputFile.getCanonicalPath();
        System.arraycopy(args, 0, argsPlusFile, i, args.length);
        argsPlusFile[argsPlusFile.length - 1] = remuxedFile.getAbsolutePath();
        return OS.getFFmpegCommand(argsPlusFile);
    }

    @Override
    public String toString() {
        String s = getName();
        if(getConfig().containsKey(FFMPEG_ARGS)) {
            s += " [" + getConfig().get(FFMPEG_ARGS) + ']';
        }
        return s;
    }
}
