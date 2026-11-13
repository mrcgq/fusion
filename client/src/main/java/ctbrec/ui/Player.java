package ctbrec.ui;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.*;
import ctbrec.event.EventBusHolder;
import ctbrec.io.StreamRedirector;
import ctbrec.io.UrlUtil;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.hls.NoStreamFoundException;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.event.PlayerStartedEvent;
import ctbrec.variableexpansion.ModelVariableExpander;
import javafx.scene.Scene;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Slf4j
public class Player {
    private static PlayerThread playerThread;
    private static Scene scene;

    private Player() {
    }

    public static boolean play(Recording rec) {
        boolean singlePlayer = Config.getInstance().getSettings().singlePlayer;
        try {
            if (singlePlayer && playerThread != null && playerThread.isRunning()) {
                playerThread.stopThread();
            }

            playerThread = new PlayerThread(rec);
            return true;
        } catch (Exception e1) {
            log.error("Couldn't start player", e1);
            return false;
        }
    }

    public static boolean play(Model model) {
        return play(model, true);
    }

    public static boolean play(Model model, boolean async) {
        try {
            if (model.isOnline(true)) {
                boolean singlePlayer = Config.getInstance().getSettings().singlePlayer;
                if (singlePlayer && playerThread != null && playerThread.isRunning()) {
                    playerThread.stopThread();
                }

                EventBusHolder.BUS.post(new PlayerStartedEvent(model));

                if (singlePlayer && playerThread != null && playerThread.isRunning()) {
                    playerThread.stopThread();
                }

                playerThread = new PlayerThread(model);
                if (!async) {
                    playerThread.join();
                }
                return true;
            } else {
                Dialogs.showError(scene, "Room not public", "Room is currently not public", null);
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Couldn't get stream information for model {}", model, e);
            Dialogs.showError(scene, "Couldn't determine stream URL", e.getLocalizedMessage(), e);
            return false;
        } catch (Exception e) {
            log.error("Couldn't get stream information for model {}", model, e);
            Dialogs.showError(scene, "Couldn't determine stream URL", e.getLocalizedMessage(), e);
            return false;
        }
    }

    public static void stop() {
        if (playerThread != null) {
            playerThread.stopThread();
        }
    }

    private static class PlayerThread extends Thread {
        @Getter
        private boolean running = false;
        private Process playerProcess;
        private Recording rec;
        private Model model;

        PlayerThread(Model model) {
            this.model = model;
            setName(getClass().getName());
            start();
        }

        PlayerThread(Recording rec) {
            this.rec = rec;
            this.model = rec.getModel();
            setName(getClass().getName());
            start();
        }

        @Override
        public void run() {
            running = true;
            Runtime rt = Runtime.getRuntime();
            Config cfg = Config.getInstance();
            try {
                if (cfg.getSettings().localRecording && rec != null) {
                    File file = rec.getAbsoluteFile();
                    String[] cmdline = createCmdline(file.getAbsolutePath(), model);
                    playerProcess = rt.exec(cmdline, OS.getEnvironment(), file.getParentFile());
                } else {
                    String url = null;
                    if (rec != null) {
                        url = getRemoteRecordingUrl(rec, cfg);
                        model = rec.getModel();
                    } else if (model != null) {
                        url = getPlaylistUrl(model);
                    }
                    log.debug("Playing {}", url);
                    String[] cmdline = createCmdline(url, model);
                    log.debug("Player command line: {}", Arrays.toString(cmdline));
                    playerProcess = rt.exec(cmdline);
                }

                // create threads, which read stdout and stderr of the player process. these are needed,
                // because otherwise the internal buffer for these streams fill up and block the process
                Thread std = new Thread(new StreamRedirector(playerProcess.getInputStream(), OutputStream.nullOutputStream()));
                std.setName("Player stdout pipe");
                std.setDaemon(true);
                std.start();
                Thread err = new Thread(new StreamRedirector(playerProcess.getErrorStream(), OutputStream.nullOutputStream()));
                err.setName("Player stderr pipe");
                err.setDaemon(true);
                err.start();

                playerProcess.waitFor();
                log.debug("Media player finished.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Error in player thread", e);
                Dialogs.showError(scene, "Playback failed", "Couldn't start playback", e);
            } catch (Exception e) {
                log.error("Error in player thread", e);
                Dialogs.showError(scene, "Playback failed", "Couldn't start playback", e);
            }
            running = false;
        }

        private static String getPlaylistUrl(Model model) throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
            List<StreamSource> sources = model.getStreamSources();
            Collections.sort(sources);
            StreamSource best;
            int maxRes = Config.getInstance().getSettings().maximumResolutionPlayer;
            if (maxRes > 0 && !sources.isEmpty()) {
                for (Iterator<StreamSource> iterator = sources.iterator(); iterator.hasNext(); ) {
                    StreamSource streamSource = iterator.next();
                    if (streamSource.getHeight() > 0 && maxRes < streamSource.getHeight()) {
                        log.trace("Res too high {} > {}", streamSource.getHeight(), maxRes);
                        iterator.remove();
                    }
                }
            }
            if (sources.isEmpty()) {
                throw new NoStreamFoundException("No stream left in playlist, because player resolution is set to " + maxRes);
            } else {
                log.debug("{} selected {}", model.getName(), sources.get(sources.size() - 1));
                best = sources.get(sources.size() - 1);
            }
            return best.getMediaPlaylistUrl();
        }

        private void expandPlaceHolders(String[] cmdline) {
            ModelVariableExpander expander = new ModelVariableExpander(model, CamrecApplication.modelNotesService, null, null);
            for (int i = 1; i < cmdline.length; i++) {
                var param = cmdline[i];
                param = expander.expand(param);
                cmdline[i] = param;
            }
        }

        private String[] createCmdline(String mediaSource, Model model) {
            Config cfg = Config.getInstance();
            String params = cfg.getSettings().mediaPlayerParams.trim();

            String[] cmdline;
            if (params.isEmpty()) {
                cmdline = new String[2];
            } else {
                String[] playerArgs = StringUtil.splitParams(params);
                cmdline = new String[playerArgs.length + 2];
                System.arraycopy(playerArgs, 0, cmdline, 1, playerArgs.length);
            }
            cmdline[0] = cfg.getSettings().mediaPlayer;
            cmdline[cmdline.length - 1] = mediaSource;
            if (model != null) {
                expandPlaceHolders(cmdline);
            }
            return cmdline;
        }

        private String getRemoteRecordingUrl(Recording rec, Config cfg)
                throws MalformedURLException, InvalidKeyException, NoSuchAlgorithmException, UnsupportedEncodingException {
            String hlsBase = Config.getInstance().getServerUrl() + "/hls";
            String recUrl = hlsBase + '/' + rec.getId() + (rec.isSingleFile() ? "" : "/playlist.m3u8");
            if (cfg.getSettings().requireAuthentication) {
                recUrl = UrlUtil.addHmac(recUrl, cfg);
            }
            return recUrl;
        }

        public void stopThread() {
            if (playerProcess != null) {
                playerProcess.destroy();
            }
        }
    }

    public static void setScene(Scene scene) {
        Player.scene = scene;
    }
}
