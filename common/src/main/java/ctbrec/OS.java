package ctbrec;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OS {

    private static final Logger LOG = LoggerFactory.getLogger(OS.class);

    private static final String USER_HOME = "user.home";
    private static final String OS_NAME = "os.name";
    private static final String CTBREC = "ctbrec";

    public enum TYPE {
        LINUX,
        MAC,
        WINDOWS,
        OTHER
    }

    private OS() {}

    public static TYPE getOsType() {
        if(System.getProperty(OS_NAME).contains("Linux")) {
            return TYPE.LINUX;
        } else if(System.getProperty(OS_NAME).contains("Windows")) {
            return TYPE.WINDOWS;
        } else if(System.getProperty(OS_NAME).contains("Mac")) {
            return TYPE.MAC;
        } else {
            return TYPE.OTHER;
        }
    }

    public static File getConfigDir() {
        File configDir;
        switch (getOsType()) {
        case LINUX:
            String userHome = System.getProperty(USER_HOME);
            configDir = new File(new File(userHome, ".config"), CTBREC);
            break;
        case MAC:
            userHome = System.getProperty(USER_HOME);
            configDir = new File(userHome, "Library/Preferences/ctbrec");
            break;
        case WINDOWS:
            String appData = System.getenv("APPDATA");
            configDir = new File(appData, CTBREC);
            break;
        default:
            throw new UnsupportedOperatingSystemException(System.getProperty(OS_NAME));
        }
        return configDir;
    }

    public static String[] getBrowserCommand(String...args) {
        if(System.getenv("CTBREC_BROWSER") != null) {
            String[] cmd = new String[args.length + 1];
            cmd[0] = System.getenv("CTBREC_BROWSER");
            System.arraycopy(args, 0, cmd, 1, args.length);
            return cmd;
        }

        try {
            URI uri = OS.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File jar = new File(uri.getPath());
            File browserDir = new File(jar.getParentFile(), "browser");
            String[] cmd;
            switch (getOsType()) {
            case LINUX:
                cmd = new String[args.length + 1];
                cmd[0] = new File(browserDir, "ctbrec-minimal-browser").getAbsolutePath();
                System.arraycopy(args, 0, cmd, 1, args.length);
                break;
            case WINDOWS:
                cmd = new String[args.length + 1];
                cmd[0] = new File(browserDir, "ctbrec-minimal-browser.exe").getAbsolutePath();
                System.arraycopy(args, 0, cmd, 1, args.length);
                break;
            case MAC:
                cmd = new String[args.length + 5];
                int index = 0;
                cmd[index++] = "open";
                cmd[index++] = "-W";
                cmd[index++] = "-a";
                cmd[index++] = new File(browserDir, "ctbrec-minimal-browser.app").getAbsolutePath();
                if (args.length > 0) {
                    cmd[index] = "--args";
                    System.arraycopy(args, 0, cmd, 5, args.length);
                }
                break;
            default:
                throw new UnsupportedOperatingSystemException(System.getProperty(OS_NAME));
            }
            LOG.debug("Browser command: {}", Arrays.toString(cmd));
            return cmd;
        } catch (URISyntaxException e) {
            throw new ForkProcessException(e);
        }
    }

    public static String[] getFFmpegCommand(String...args) {
        if(System.getenv("CTBREC_FFMPEG") != null) {
            String[] cmd = new String[args.length + 1];
            cmd[0] = System.getenv("CTBREC_FFMPEG");
            System.arraycopy(args, 0, cmd, 1, args.length);
            return cmd;
        }

        try {
            URI uri = OS.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            File jar = new File(uri.getPath());
            File browserDir = new File(jar.getParentFile(), "ffmpeg");
            String[] cmd;
            switch (getOsType()) {
            case LINUX:
            case MAC:
                cmd = new String[args.length + 1];
                cmd[0] = new File(browserDir, "ffmpeg").getAbsolutePath();
                System.arraycopy(args, 0, cmd, 1, args.length);
                break;
            case WINDOWS:
                cmd = new String[args.length + 1];
                cmd[0] = new File(browserDir, "ffmpeg.exe").getAbsolutePath();
                System.arraycopy(args, 0, cmd, 1, args.length);
                break;
            default:
                throw new UnsupportedOperatingSystemException(System.getProperty(OS_NAME));
            }
            LOG.debug("FFmpeg command: {}", Arrays.toString(cmd));
            return cmd;
        } catch (URISyntaxException e) {
            throw new ForkProcessException(e);
        }
    }

    public static Settings getDefaultSettings() {
        Settings settings = new Settings();
        if(getOsType() == TYPE.WINDOWS) {
            String userHome = System.getProperty(USER_HOME);
            Path path = Paths.get(userHome, "Videos", CTBREC);
            settings.recordingsDir = path.toString();
            String programFiles = System.getenv("ProgramFiles");
            programFiles = programFiles != null ? programFiles : "C:\\Program Files";
            settings.mediaPlayer = Paths.get(programFiles, "VideoLAN", "VLC", "vlc.exe").toString();
        } else if(getOsType() == TYPE.MAC) {
            String userHome = System.getProperty(USER_HOME);
            settings.recordingsDir = Paths.get(userHome, "Movies", CTBREC).toString();
            settings.mediaPlayer = "/Applications/VLC.app/Contents/MacOS/VLC";
        }
        return settings;
    }

    public static String[] getEnvironment() {
        String[] env = new String[System.getenv().size()];
        int index = 0;
        for (Entry<String, String> entry : System.getenv().entrySet()) {
            env[index++] = entry.getKey() + "=" + entry.getValue();
        }
        return env;
    }
}
