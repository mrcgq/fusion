package ctbrec.recorder.download.dash;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.OS;
import ctbrec.io.StreamRedirector;
import ctbrec.recorder.download.ProcessExitedUncleanException;

public class FfmpegMuxer {
    private static final Logger LOG = LoggerFactory.getLogger(FfmpegMuxer.class);
    private static final int ALL_GOOD = 0;

    File segmentDir;

    public FfmpegMuxer(File segmentDir, File targetFile) throws IOException {
        this.segmentDir = segmentDir;
        if (!segmentDir.exists()) {
            throw new IOException("Directory does not exist " + segmentDir);
        }
        if (!segmentDir.isDirectory()) {
            throw new IOException(segmentDir + " is not a directory");
        }

        String[] videoSegments = Optional.ofNullable(segmentDir.list((dir, name) -> name.startsWith("video_"))).orElse(new String[0]);
        Arrays.sort(videoSegments);
        String[] audioSegments = Optional.ofNullable(segmentDir.list((dir, name) -> name.startsWith("audio_"))).orElse(new String[0]);
        Arrays.sort(audioSegments);

        File mp4VideoTrack = new File(segmentDir, "video.mp4");
        mergeSegments(videoSegments, mp4VideoTrack);
        File mp4AudioTrack = new File(segmentDir, "audio.mp4");
        mergeSegments(audioSegments, mp4AudioTrack);

        int exitCode = mergeTracks(mp4VideoTrack, mp4AudioTrack, targetFile);
        if (exitCode == ALL_GOOD) {
            LOG.debug("Deleting merged video and audio tracks");
            Files.delete(mp4VideoTrack.toPath());
            Files.delete(mp4AudioTrack.toPath());
            Files.deleteIfExists(new File(segmentDir, "merge.log").toPath());
            LOG.debug("Deleting segments");
            deleteFiles(segmentDir, videoSegments);
            deleteFiles(segmentDir, audioSegments);
            if (segmentDir.list().length == 0) {
                Files.delete(segmentDir.toPath());
            }
        } else {
            throw new ProcessExitedUncleanException("FFMPEG exited with " + exitCode);
        }
    }

    private void deleteFiles(File dir, String[] files) {
        for (String file : files) {
            File absFile = new File(dir, file);
            try {
                Files.delete(absFile.toPath());
            } catch(IOException e) {
                LOG.info("Couldn't delete segment {}", absFile, e);
            }
        }
    }

    private void mergeSegments(String[] segments, File toFile) throws IOException {
        try (FileOutputStream out = new FileOutputStream(toFile)) {
            for (String segment : segments) {
                try (FileInputStream in = new FileInputStream(new File(segmentDir, segment))) {
                    byte[] buffer = new byte[1024];
                    int len = -1;
                    while ((len = in.read(buffer)) >= 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    private int mergeTracks(File mp4VideoTrack, File mp4AudioTrack, File output) throws IOException {
        File dir = mp4VideoTrack.getParentFile();
        File mergeLog = new File(dir, "merge.log");
        try (FileOutputStream muxLogStream = new FileOutputStream(mergeLog)) {
            // @formatter:off
            String[] cmdline = OS.getFFmpegCommand(
                    "-i", mp4VideoTrack.getCanonicalPath(),
                    "-i", mp4AudioTrack.getCanonicalPath(),
                    "-c:v", "copy",
                    "-c:a", "copy",
                    "-movflags", "faststart", // for streaming
                    "-y", // overwrite existing files
                    output.getCanonicalPath()
                    );
            // @formatter:on
            LOG.debug("Command line: {}", Arrays.toString(cmdline));
            Process ffmpeg = Runtime.getRuntime().exec(cmdline);
            Thread stdout = new Thread(new StreamRedirector(ffmpeg.getInputStream(), muxLogStream));
            Thread stderr = new Thread(new StreamRedirector(ffmpeg.getErrorStream(), muxLogStream));
            stdout.start();
            stderr.start();
            int exitCode = ffmpeg.waitFor();
            stdout.join();
            stderr.join();
            return exitCode;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Interrupted while waiting for FFMPEG", e);
            return 1;
        }
    }
}
