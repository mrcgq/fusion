package ctbrec.recorder.download;

import ctbrec.OS;
import ctbrec.io.DevNull;
import ctbrec.io.StreamRedirector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VideoLengthDetector {
    private static final Logger LOG = LoggerFactory.getLogger(VideoLengthDetector.class);

    private VideoLengthDetector() {
    }

    /**
     * Tries to determine the length of a video file by calling FFmpeg and parsing it's output
     *
     * @param videoFile the video file to analyze
     * @return the length as Duration object. The duration is negative, if the length couldn't be determined
     */
    public static Duration getLength(File videoFile) {
        try {
            String[] args = {
                    "-hide_banner",
                    "-i",
                    videoFile.getCanonicalPath(),
                    "-f",
                    "ffmetadata",
                    "-"
            };
            String[] cmdline = OS.getFFmpegCommand(args);
            LOG.debug("Command line: {}", Arrays.toString(cmdline));
            Process ffmpeg = Runtime.getRuntime().exec(cmdline, new String[0], videoFile.getParentFile());
            int exitCode;
            ByteArrayOutputStream stdErrBuffer = new ByteArrayOutputStream();
            Thread stdout = new Thread(new StreamRedirector(ffmpeg.getInputStream(), new DevNull()));
            Thread stderr = new Thread(new StreamRedirector(ffmpeg.getErrorStream(), stdErrBuffer));
            stdout.start();
            stderr.start();
            exitCode = ffmpeg.waitFor();
            LOG.debug("FFmpeg exited with code {}", exitCode);
            stdout.join();
            stderr.join();
            String ffmpegStderr = stdErrBuffer.toString();
            return parseDuration(ffmpegStderr);
        } catch (IOException | ProcessExitedUncleanException e) {
            LOG.error("Error in FFMpeg thread", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.info("Interrupted while waiting for ffmpeg", e);
        }
        return Duration.ZERO.minusSeconds(1);
    }

    private static Duration parseDuration(String ffmpegStderr) {
        Matcher m = Pattern.compile("Duration: (\\d{2}):(\\d{2}):(\\d{2}).(\\d{2}),").matcher(ffmpegStderr);
        if (m.find()) {
            int hours = Integer.parseInt(m.group(1));
            int minutes = Integer.parseInt(m.group(2));
            int seconds = Integer.parseInt(m.group(3));
            int millis = Integer.parseInt(m.group(4));
            return Duration.ofHours(hours)
                    .plusMinutes(minutes)
                    .plusSeconds(seconds)
                    .plusMillis(millis);
        }
        return Duration.ofSeconds(-1);
    }
}
