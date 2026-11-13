package ctbrec.ui;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.eventbus.Subscribe;
import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.Version;
import ctbrec.event.Event;
import ctbrec.event.EventBusHolder;
import ctbrec.event.EventHandler;
import ctbrec.event.EventHandlerConfiguration;
import ctbrec.image.LocalPortraitStore;
import ctbrec.image.PortraitStore;
import ctbrec.image.RemotePortraitStore;
import ctbrec.io.*;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.notes.LocalModelNotesService;
import ctbrec.notes.ModelNotesService;
import ctbrec.notes.RemoteModelNotesService;
import ctbrec.recorder.OnlineMonitor;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.RemoteRecorder;
import ctbrec.recorder.SimplifiedLocalRecorder;
import ctbrec.sites.Site;
import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.sites.bonga.BongaCams;
import ctbrec.sites.cam4.Cam4;
import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.sites.manyvids.MVLive;
import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.sites.secretfriends.SecretFriends;
import ctbrec.sites.showup.Showup;
import ctbrec.sites.streamate.Streamate;
import ctbrec.sites.streamray.Streamray;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.sites.winktv.WinkTv;
import ctbrec.sites.xlovecam.XloveCam;
import ctbrec.ui.controls.Dialogs;
import ctbrec.ui.news.NewsTab;
import ctbrec.ui.settings.SettingsTab;
import ctbrec.ui.tabs.*;
import ctbrec.ui.tabs.logging.LoggingTab;
import ctbrec.ui.tabs.recorded.RecordedTab;
import javafx.application.Application;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import lombok.Data;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static ctbrec.event.Event.Type.*;
import static javafx.scene.control.TabPane.TabDragPolicy.FIXED;
import static javafx.scene.control.TabPane.TabDragPolicy.REORDER;

public class CamrecApplication extends Application {

    static final Logger LOG = LoggerFactory.getLogger(CamrecApplication.class);

    private Config config;
    private Recorder recorder;
    private OnlineMonitor onlineMonitor;
    static HostServices hostServices;
    private final BorderPane rootPane = new BorderPane();
    private final HBox statusBar = new HBox();
    private final Label statusLabel = new Label();
    private final TabPane tabPane = new TabPane();
    private final List<Site> sites = new ArrayList<>();
    public static HttpClient httpClient;
    public static PortraitStore portraitStore;
    public static ModelNotesService modelNotesService;
    public static String title;
    private Stage primaryStage;
    private RecordingsTab recordingsTab;
    private ScheduledExecutorService scheduler;
    private int activeRecordings = 0;
    private double bytesPerSecond = 0;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            var t = new Thread(r);
            t.setDaemon(true);
            t.setName("Scheduler");
            return t;
        });

        logEnvironment();
        createSites();
        loadConfig();
        registerAlertSystem();
        registerActiveRecordingsCounter();
        registerBandwidthMeterListener();
        createHttpClient();
        hostServices = getHostServices();
        createRecorder();
        initSites();
        startOnlineMonitor();
        createPortraitStore();
        createModelNotesService();
        createGui(primaryStage);
        if (config.getSettings().checkForUpdates) {
            checkForUpdates();
        }
        registerClipboardListener();
        registerTrayIconListener();
    }

    private void createPortraitStore() {
        if (config.getSettings().localRecording) {
            portraitStore = new LocalPortraitStore(config);
        } else {
            portraitStore = new RemotePortraitStore(httpClient, config);
        }
    }

    private void createModelNotesService() {
        if (config.getSettings().localRecording) {
            modelNotesService = new LocalModelNotesService(config);
        } else {
            modelNotesService = new RemoteModelNotesService(httpClient, config);
        }
    }

    private void registerTrayIconListener() {
        EventBusHolder.BUS.register(new Object() {
            @Subscribe
            public void trayActionRequest(Map<String, Object> evt) {
                if (Objects.equals("shutdown", evt.get("event"))) {
                    LOG.debug("Shutdown request from tray icon");
                    try {
                        Platform.runLater(() -> {
                            primaryStage.show();
                            shutdown();
                        });
                    } catch (Exception ex) {
                        LOG.error(ex.getMessage(), ex);
                    }
                }
                if (Objects.equals("stage_restored", evt.get("event"))) {
                    LOG.debug("Main stage restored");
                    Platform.runLater(() -> {
                        if (tabPane.getSelectionModel().getSelectedItem() instanceof TabSelectionListener listener) {
                            listener.selected();
                        }
                    });
                }
            }
        });
    }

    private void createSites() {
        sites.add(new AmateurTv());
        sites.add(new BongaCams());
        sites.add(new Cam4());
        sites.add(new Camsoda());
        sites.add(new Chaturbate());
        sites.add(new CherryTv());
        sites.add(new Dreamcam());
        sites.add(new Fc2Live());
        sites.add(new Flirt4Free());
        sites.add(new LiveJasmin());
        sites.add(new MVLive());
        sites.add(new MyFreeCams());
        sites.add(new SecretFriends());
        sites.add(new Showup());
        sites.add(new Streamate());
        sites.add(new Stripchat());
        sites.add(new Streamray());
        sites.add(new WinkTv());
        sites.add(new XloveCam());
    }

    private void registerClipboardListener() {
        if (config.getSettings().monitorClipboard) {
            var clipboardListener = new ClipboardListener(recorder, sites);
            scheduler.scheduleAtFixedRate(clipboardListener, 0, 1, TimeUnit.SECONDS);
        }
    }

    private void initSites() {
        sites.forEach(site -> {
            try {
                site.setRecorder(recorder);
                site.setConfig(config);
                if (site.isEnabled()) {
                    site.init();
                }
            } catch (Exception e) {
                LOG.error("Error while initializing site {}", site.getName(), e);
            }
        });
    }

    private void startOnlineMonitor() {
        onlineMonitor = new OnlineMonitor(recorder, config);
        onlineMonitor.start();
    }

    private void logEnvironment() {
        LOG.debug("OS:\t{} {}", System.getProperty("os.name"), System.getProperty("os.version"));
        LOG.debug("Java:\t{} {} {}", System.getProperty("java.vendor"), System.getProperty("java.vm.name"), System.getProperty("java.version"));
        LOG.debug("JavaFX:\t{} ({})", System.getProperty("javafx.version"), System.getProperty("javafx.runtime.version"));
    }

    private void createGui(Stage primaryStage) throws IOException {
        LOG.debug("Creating GUI");
        DesktopIntegration.setRecorder(recorder);
        DesktopIntegration.setPrimaryStage(primaryStage);
        CamrecApplication.title = "CTB Recorder " + Version.getVersion();
        primaryStage.setTitle(title);
        InputStream icon = getClass().getResourceAsStream("/icon.png");
        primaryStage.getIcons().add(new Image(icon));
        int windowWidth = Config.getInstance().getSettings().windowWidth;
        int windowHeight = Config.getInstance().getSettings().windowHeight;

        var scene = new Scene(rootPane, windowWidth, windowHeight);
        primaryStage.setScene(scene);
        Dialogs.setScene(scene);
        rootPane.setCenter(tabPane);
        rootPane.setBottom(statusBar);
        for (Site site : sites) {
            if (site.isEnabled()) {
                var siteTab = new SiteTab(site, scene);
                tabPane.getTabs().add(siteTab);
            }
        }

        var modelsTab = new RecordedTab(recorder, sites);
        tabPane.getTabs().add(modelsTab);
        recordingsTab = new RecordingsTab("Recordings", recorder, config, modelNotesService);
        tabPane.getTabs().add(recordingsTab);
        if (config.getSettings().recentlyWatched) {
            tabPane.getTabs().add(new RecentlyWatchedTab(recorder, sites));
        }
        tabPane.getTabs().add(new SettingsTab(sites, recorder));
        //tabPane.getTabs().add(new NewsTab(config));
        tabPane.getTabs().add(new NewsTab(config, getHostServices()));
        tabPane.getTabs().add(new DonateTabFx());
        tabPane.getTabs().add(new HelpTab());
        tabPane.getTabs().add(new LoggingTab());
        tabPane.setTabDragPolicy(config.getSettings().tabsSortable ? REORDER : FIXED);

        restoreTabOrder();
        switchToStartTab();
        writeColorSchemeStyleSheet();
        var base = Color.web(Config.getInstance().getSettings().colorBase);
        if (!base.equals(Color.WHITE)) {
            loadStyleSheet(primaryStage, "color.css");
        }
        loadStyleSheet(primaryStage, "style.css");
        loadStyleSheet(primaryStage, "font.css");
        primaryStage.getScene().getStylesheets().add("/ctbrec/ui/settings/ColorSettingsPane.css");
        primaryStage.getScene().getStylesheets().add("/ctbrec/ui/controls/SearchBox.css");
        primaryStage.getScene().getStylesheets().add("/ctbrec/ui/controls/Popover.css");
        primaryStage.getScene().getStylesheets().add("/ctbrec/ui/settings/api/Preferences.css");
        primaryStage.getScene().getStylesheets().add("/ctbrec/ui/tabs/ThumbCell.css");
        primaryStage.getScene().widthProperty().addListener((observable, oldVal, newVal) -> Config.getInstance().getSettings().windowWidth = newVal.intValue());
        primaryStage.getScene().heightProperty()
                .addListener((observable, oldVal, newVal) -> Config.getInstance().getSettings().windowHeight = newVal.intValue());
        primaryStage.setMaximized(Config.getInstance().getSettings().windowMaximized);
        primaryStage.maximizedProperty().addListener((observable, oldVal, newVal) -> Config.getInstance().getSettings().windowMaximized = newVal);
        Player.setScene(primaryStage.getScene());
        primaryStage.setX(Config.getInstance().getSettings().windowX);
        primaryStage.setY(Config.getInstance().getSettings().windowY);
        primaryStage.xProperty().addListener((observable, oldVal, newVal) -> {
            if (newVal.doubleValue() + primaryStage.getWidth() > 0) {
                Config.getInstance().getSettings().windowX = newVal.intValue();
            }
        });
        primaryStage.yProperty().addListener((observable, oldVal, newVal) -> {
            if (newVal.doubleValue() + primaryStage.getHeight() > 0) {
                Config.getInstance().getSettings().windowY = newVal.intValue();
            }
        });

        if (config.getSettings().startMinimized) {
            LOG.info("Minimize to tray on start");
            DesktopIntegration.minimizeToTray(primaryStage);
        } else {
            LOG.info("Showing primary stage");
            primaryStage.show();
        }
        primaryStage.setOnCloseRequest(createShutdownHandler());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Platform.runLater(this::shutdown)));
        setWindowMinimizeListener(primaryStage);

        // register changelistener to activate / deactivate tabs, when the user switches between them
        tabPane.getSelectionModel().selectedItemProperty().addListener(this::tabChanged);

        statusBar.getChildren().add(statusLabel);
        HBox.setMargin(statusLabel, new Insets(10, 10, 10, 10));

        Optional.ofNullable(SplashScreen.getSplashScreen()).ifPresent(SplashScreen::close);
    }

    private void setWindowMinimizeListener(Stage primaryStage) {
        primaryStage.iconifiedProperty().addListener((obs, oldV, newV) -> {
            if (Boolean.TRUE.equals(newV) && Config.getInstance().getSettings().minimizeToTray && primaryStage.isShowing()) {
                DesktopIntegration.minimizeToTray(primaryStage);
                suspendTabUpdates();
            }
        });

    }

    private void tabChanged(ObservableValue<?> ov, Tab from, Tab to) {
        try {
            if (from instanceof TabSelectionListener l) {
                l.deselected();
            }
            if (to instanceof TabSelectionListener l) {
                l.selected();
            }
        } catch (Exception e) {
            LOG.error("Error switching tabs", e);
        }
    }

    private void suspendTabUpdates() {
        tabPane.getTabs().stream()
                .filter(TabSelectionListener.class::isInstance)
                .forEach(t -> ((TabSelectionListener) t).deselected());
    }

    private javafx.event.EventHandler<WindowEvent> createShutdownHandler() {
        return e -> {
            e.consume();
            shutdown();
        };
    }

    private void shutdown() {
        // check for active downloads
        if (recordingsTab.isDownloadRunning()) {
            boolean exitAnyway = Dialogs.showConfirmDialog("Shutdown", "Do you want to exit anyway?", "There are downloads running", primaryStage.getScene());
            if (!exitAnyway) {
                return;
            }
        }

        // check for active recordings
        var shutdownNow = false;
        if (config.getSettings().localRecording) {
            try {
                if (!recorder.getCurrentlyRecording().isEmpty()) {
                    ButtonType result = Dialogs.showShutdownDialog(primaryStage.getScene());
                    if (result == ButtonType.NO) {
                        return;
                    } else if (result == ButtonType.YES) {
                        shutdownNow = true;
                    }
                }
            } catch (InvalidKeyException | NoSuchAlgorithmException | IOException ex) {
                LOG.warn("Couldn't check, if recordings are running");
            }
        }

        Alert shutdownInfo = new AutosizeAlert(Alert.AlertType.INFORMATION, primaryStage.getScene());
        shutdownInfo.setTitle("Shutdown");
        shutdownInfo.setContentText("Shutting down. Please wait while recordings are finished...");
        shutdownInfo.show();
        final boolean immediately = shutdownNow;
        new Thread(() -> {
            List<String> tabOrder = Config.getInstance().getSettings().tabOrder;
            tabOrder.clear();
            for (Tab tab : tabPane.getTabs()) {
                tabOrder.add(tab.getText());
                if (tab instanceof ShutdownListener l) {
                    l.onShutdown();
                }
            }
            onlineMonitor.shutdown();
            recorder.shutdown(immediately);
            scheduler.shutdownNow();
            for (Site site : sites) {
                if (site.isEnabled()) {
                    site.shutdown();
                }
            }
            try {
                Config.getInstance().save();
                clearHttpCache();
                LOG.info("Shutdown complete. Goodbye!");
                Platform.runLater(() -> {
                    primaryStage.close();
                    shutdownInfo.close();
                    Platform.exit();
                    // This is needed, because OkHttp?! seems to block the shutdown with its writer threads. They are not daemon threads :(
                    System.exit(0);
                });
            } catch (IOException e1) {
                Platform.runLater(() -> {
                    Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, primaryStage.getScene());
                    alert.setTitle("Error saving settings");
                    alert.setContentText("Couldn't save settings: " + e1.getLocalizedMessage());
                    alert.showAndWait();
                    System.exit(1);
                });
            }
            try {
                ExternalBrowser.getInstance().close();
            } catch (IOException e12) {
                // noop
            }
        }).start();

    }

    private void clearHttpCache() throws IOException {
        try {
            File httpCacheDir = new File(config.getConfigDir(), "cache");
            LOG.debug("Deleting http cache {}", httpCacheDir);
            HttpClientCacheProvider.getCache(config).evictAll();
            HttpClientCacheProvider.getCache(config).close();
            IoUtils.deleteDirectory(httpCacheDir);
            LOG.debug("Cache has been deleted");
        } catch (Exception e) {
            LOG.info("The HTTP cache was not completely deleted: {}", e.getLocalizedMessage());
        }
    }

    private void registerAlertSystem() {
        for (EventHandlerConfiguration eventHandlerConfig : Config.getInstance().getSettings().eventHandlers) {
            var handler = new EventHandler(eventHandlerConfig);
            EventBusHolder.register(handler);
            LOG.debug("Registered event handler for {} {}", eventHandlerConfig.getEvent(), eventHandlerConfig.getName());
        }
        LOG.debug("Alert System registered");
    }

    private void registerActiveRecordingsCounter() {
        EventBusHolder.BUS.register(new Object() {
            @Subscribe
            public void handleEvent(Event evt) {
                if (evt.getType() == MODEL_ONLINE || evt.getType() == MODEL_STATUS_CHANGED || evt.getType() == RECORDING_STATUS_CHANGED) {
                    try {
                        int modelCount = recorder.getModelCount();
                        List<Model> currentlyRecording = recorder.getCurrentlyRecording();
                        activeRecordings = currentlyRecording.size();
                        DesktopIntegration.updateTrayIcon(activeRecordings);
                        String windowTitle = getActiveRecordings(activeRecordings, modelCount) + title;
                        Platform.runLater(() -> primaryStage.setTitle(windowTitle));
                        updateStatus();
                    } catch (Exception e) {
                        LOG.warn("Couldn't update window title", e);
                    }
                }
            }

            private String getActiveRecordings(int activeRecordings, int modelCount) {
                if (activeRecordings > 0) {
                    StringBuilder s = new StringBuilder("(").append(activeRecordings);
                    if (config.getSettings().totalModelCountInTitle) {
                        s.append("/").append(modelCount);
                    }
                    s.append(") ");
                    return s.toString();
                } else {
                    return "";
                }
            }
        });
    }

    private void registerBandwidthMeterListener() {
        BandwidthMeter.addListener((bytes, dur) -> {
            long millis = dur.toMillis();
            double bytesPerMilli = bytes / (double) millis;
            bytesPerSecond = bytesPerMilli * 1000;
            updateStatus();
        });
    }

    private void updateStatus() {
        if (activeRecordings == 0) {
            bytesPerSecond = 0;
        }
        String humanReadable = ByteUnitFormatter.format(bytesPerSecond);
        var status = String.format("Recording %s / %s models (%s grouped) @ %s/s", activeRecordings, recorder.getModelCount(), recorder.getGroupedModelCount(), humanReadable);
        Platform.runLater(() -> statusLabel.setText(status));
    }

    private void writeColorSchemeStyleSheet() {
        var colorCss = new File(Config.getInstance().getConfigDir(), "color.css");
        try (var fos = new FileOutputStream(colorCss)) {
            String content = ".root {\n" + "    -fx-base: " + Config.getInstance().getSettings().colorBase + ";\n" + "    -fx-accent: "
                    + Config.getInstance().getSettings().colorAccent + ";\n" + "    -fx-default-button: -fx-accent;\n" + "    -fx-focus-color: -fx-accent;\n"
                    + "    -fx-control-inner-background-alt: derive(-fx-base, 95%);\n" + "}";
            fos.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            LOG.error("Couldn't write stylesheet for user defined color theme");
        }
    }

    public static void loadStyleSheet(Stage primaryStage, String filename) {
        var css = new File(Config.getInstance().getConfigDir(), filename);
        if (css.exists() && css.isFile()) {
            primaryStage.getScene().getStylesheets().add(css.toURI().toString());
        }
    }

    private void restoreTabOrder() {
        List<String> tabOrder = Config.getInstance().getSettings().tabOrder;
        for (int i = 0; i < tabOrder.size(); i++) {
            Tab matched = null;
            for (Tab tab : tabPane.getTabs()) {
                if (Objects.equals(tabOrder.get(i), tab.getText())) {
                    matched = tab;
                }
            }
            if (matched != null) {
                tabPane.getTabs().remove(matched);
                int max = tabPane.getTabs().size();
                tabPane.getTabs().add(Math.min(i, max), matched);
            }
        }
    }

    private void switchToStartTab() {
        String startTab = Config.getInstance().getSettings().startTab;
        if (StringUtil.isNotBlank(startTab)) {
            for (Tab tab : tabPane.getTabs()) {
                if (Objects.equals(startTab, tab.getText())) {
                    tabPane.getSelectionModel().select(tab);
                    break;
                }
            }
        }
        if (tabPane.getSelectionModel().getSelectedItem() instanceof TabSelectionListener l) {
            l.selected();
        }
    }

    private void createRecorder() {
        if (config.getSettings().localRecording) {
            try {
                recorder = new SimplifiedLocalRecorder(config, sites);
            } catch (IOException e) {
                LOG.error("Couldn't initialize recorder", e);
                Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, primaryStage.getScene());
                alert.setTitle("Whoopsie");
                alert.setContentText("Couldn't initialize recorder: " + e.getLocalizedMessage());
                alert.showAndWait();
            }
        } else {
            recorder = new RemoteRecorder(config, httpClient, sites);
        }
    }

    private void loadConfig() {
        try {
            Config.init(sites);
        } catch (Exception e) {
            LOG.error("Couldn't load settings", e);
            Alert alert = new AutosizeAlert(Alert.AlertType.ERROR, primaryStage.getScene());
            alert.setTitle("Whoopsie");
            alert.setContentText("Couldn't load settings. Falling back to defaults. A backup of your settings has been created.");
            alert.showAndWait();
        }
        config = Config.getInstance();
    }

    private void createHttpClient() {
        httpClient = new HttpClient("camrec", config) {
            @Override
            public boolean login() {
                return false;
            }
        };
    }

    public static void main(String[] args) {
        launch(args);
    }

    private void checkForUpdates() {
        var updateCheck = new Thread(() -> {
            var url = "https://pastebin.com/raw/mUxtKzyB";
            var request = new Request.Builder().url(url).build();
            try (var response = httpClient.execute(request)) {
                var body = response.body().string();
                LOG.trace("Version check respone: {}", body);
                if (response.isSuccessful()) {
                    List<Release> releases = ObjectMapperFactory.getMapper().readValue(body, new TypeReference<>() {
                    });
                    var latest = releases.get(0);
                    var latestVersion = latest.getVersion();
                    var ctbrecVersion = Version.getVersion();
                    if (latestVersion.compareTo(ctbrecVersion) > 0) {
                        LOG.debug("Update available {} < {}", ctbrecVersion, latestVersion);
                        Platform.runLater(() -> tabPane.getTabs().add(new UpdateTab(latest)));
                    } else {
                        LOG.debug("ctbrec is up-to-date {}", ctbrecVersion);
                    }
                } else {
                    throw new HttpException(response.code(), response.message());
                }
            } catch (Exception e) {
                LOG.warn("Update check failed: {}", e.getMessage());
            }
        });
        updateCheck.setName("Update Check");
        updateCheck.setDaemon(true);
        updateCheck.start();
    }

    @Data
    public static class Release {
        private String name;
        @JsonProperty("tag_name")
        private String tagName;
        @JsonProperty("html_url")
        private String htmlUrl;

        public Version getVersion() {
            return Version.of(tagName);
        }
    }
}
