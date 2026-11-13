package ctbrec.ui.settings;

import ctbrec.Config;
import ctbrec.GlobalThreadPool;
import ctbrec.Hmac;
import ctbrec.Settings;
import ctbrec.Settings.DirectoryStructure;
import ctbrec.Settings.ProxyType;
import ctbrec.docs.DocServer;
import ctbrec.recorder.Recorder;
import ctbrec.sites.Site;
import ctbrec.ui.DesktopIntegration;
import ctbrec.ui.SiteUI;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.controls.range.DiscreteRange;
import ctbrec.ui.settings.api.*;
import ctbrec.ui.sites.ConfigUI;
import ctbrec.ui.tabs.TabSelectionListener;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.Transition;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.*;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ctbrec.Settings.DirectoryStructure.*;
import static ctbrec.Settings.ProxyType.*;
import static ctbrec.Settings.SplitStrategy.*;
import static java.util.Optional.ofNullable;

public class SettingsTab extends Tab implements TabSelectionListener {

    private static final Logger LOG = LoggerFactory.getLogger(SettingsTab.class);
    public static final int CHECKBOX_MARGIN = 6;
    private static final long MiB = 1024 * 1024L; // NOSONAR
    private static final long GiB = 1024 * MiB; // NOSONAR

    private final List<Site> sites;
    private final Recorder recorder;
    private final Config config;
    private final Settings settings;
    private boolean initialized = false;

    private SimpleStringProperty flaresolverrApiUrl;
    private SimpleIntegerProperty flaresolverrTimeoutInMillis;
    private SimpleJoinedStringListProperty flaresolverrUseForDomains;
    private SimpleStringProperty httpUserAgent;
    private SimpleStringProperty httpUserAgentMobile;
    private SimpleIntegerProperty overviewUpdateIntervalInSecs;
    private SimpleBooleanProperty updateThumbnails;
    private SimpleBooleanProperty determineResolution;
    private SimpleBooleanProperty chooseStreamQuality;
    private SimpleBooleanProperty confirmationDialogs;
    private SimpleBooleanProperty livePreviews;
    private SimpleBooleanProperty monitorClipboard;
    private SimpleListProperty<String> startTab;
    private SimpleFileProperty mediaPlayer;
    private SimpleStringProperty mediaPlayerParams;
    private SimpleFileProperty browserOverride;
    private SimpleStringProperty browserParams;
    private SimpleBooleanProperty forceBrowserOverride;
    private SimpleIntegerProperty maximumResolutionPlayer;
    private SimpleBooleanProperty showPlayerStarting;
    private SimpleBooleanProperty singlePlayer;
    private SimpleListProperty<ProxyType> proxyType;
    private SimpleStringProperty proxyHost;
    private SimpleStringProperty proxyPort;
    private SimpleStringProperty proxyUser;
    private SimpleStringProperty proxyPassword;
    private SimpleDirectoryProperty recordingsDir;
    private SimpleListProperty<DirectoryStructure> directoryStructure;
    private SimpleListProperty<SplitAfterOption> splitAfter;
    private SimpleListProperty<SplitBiggerThanOption> splitBiggerThan;
    private SimpleRangeProperty<Integer> resolutionRange;
    private final List<Integer> labels = Arrays.asList(0, 240, 360, 480, 540, 600, 720, 960, 1080, 1440, 2160, 4320, 8640);
    private final List<Integer> values = Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
    private final DiscreteRange<Integer> rangeValues = new DiscreteRange<>(values, labels);
    private SimpleIntegerProperty concurrentRecordings;
    private SimpleIntegerProperty onlineCheckIntervalInSecs;
    private SimpleBooleanProperty onlineCheckSkipsPausedModels;
    private SimpleLongProperty leaveSpaceOnDevice;
    private SimpleStringProperty ffmpegParameters;
    private SimpleBooleanProperty logFFmpegOutput;
    private SimpleBooleanProperty loghlsdlOutput;
    private SimpleBooleanProperty logMissedSegments;
    private SimpleStringProperty fileExtension;
    private SimpleStringProperty server;
    private SimpleIntegerProperty port;
    private SimpleStringProperty path;
    private SimpleStringProperty downloadFilename;
    private SimpleBooleanProperty recordedModelsPerSite;
    private SimpleBooleanProperty requireAuthentication;
    private SimpleBooleanProperty totalModelCountInTitle;
    private SimpleBooleanProperty showActiveRecordingsInTray;
    private SimpleBooleanProperty transportLayerSecurity;
    private SimpleBooleanProperty fastScrollSpeed;
    private SimpleBooleanProperty useHlsdl;
    private SimpleBooleanProperty recentlyWatched;
    private SimpleFileProperty hlsdlExecutable;
    private ExclusiveSelectionProperty recordLocal;
    private SimpleIntegerProperty postProcessingThreads;
    private IgnoreList ignoreList;
    private Label restartNotification;
    private SimpleIntegerProperty playlistRequestTimeout;
    private SimpleBooleanProperty minimizeToTray;
    private SimpleBooleanProperty startMinimized;
    private SimpleBooleanProperty showGridLinesInTables;
    private SimpleBooleanProperty tabsSortable;
    private SimpleIntegerProperty defaultPriority;
    private LocalTimeProperty timeoutRecordingStartingAt;
    private LocalTimeProperty timeoutRecordingEndingAt;
    private SimpleLongProperty recordUntilDefaultDurationInMinutes;
    private SimpleStringProperty dateTimeFormat;
    private final VariablePlayGroundDialogFactory variablePlayGroundDialogFactory = new VariablePlayGroundDialogFactory();
    private SimpleBooleanProperty checkForUpdates;
    private SimpleStringProperty filterBlacklist;
    private SimpleStringProperty filterWhitelist;
    private SimpleBooleanProperty deleteOrphanedRecordingMetadata;
    private SimpleIntegerProperty restrictBitrate;
    private SimpleIntegerProperty configSavingDelayMs;
    private SimpleIntegerProperty httpClientMaxRequests;
    private SimpleIntegerProperty httpClientMaxRequestsPerHost;

    public SettingsTab(List<Site> sites, Recorder recorder) {
        this.sites = sites;
        this.recorder = recorder;
        setText("Settings");
        setClosable(false);
        config = Config.getInstance();
        settings = config.getSettings();
    }

    private void initializeProperties() {
        flaresolverrApiUrl = new SimpleStringProperty(null, "flaresolverr.apiUrl", settings.flaresolverr.apiUrl);
        flaresolverrTimeoutInMillis = new SimpleIntegerProperty(null, "flaresolverr.timeoutInMillis", settings.flaresolverr.timeoutInMillis);
        flaresolverrUseForDomains = new SimpleJoinedStringListProperty(null, "flaresolverr.useForDomains", "\n", 
                FXCollections.observableList(settings.flaresolverr.useForDomains));
        httpUserAgent = new SimpleStringProperty(null, "httpUserAgent", settings.httpUserAgent);
        httpUserAgentMobile = new SimpleStringProperty(null, "httpUserAgentMobile", settings.httpUserAgentMobile);
        overviewUpdateIntervalInSecs = new SimpleIntegerProperty(null, "overviewUpdateIntervalInSecs", settings.overviewUpdateIntervalInSecs);
        updateThumbnails = new SimpleBooleanProperty(null, "updateThumbnails", settings.updateThumbnails);
        determineResolution = new SimpleBooleanProperty(null, "determineResolution", settings.determineResolution);
        chooseStreamQuality = new SimpleBooleanProperty(null, "chooseStreamQuality", settings.chooseStreamQuality);
        livePreviews = new SimpleBooleanProperty(null, "livePreviews", settings.livePreviews);
        monitorClipboard = new SimpleBooleanProperty(null, "monitorClipboard", settings.monitorClipboard);
        startTab = new SimpleListProperty<>(null, "startTab", FXCollections.observableList(getTabNames()));
        mediaPlayer = new SimpleFileProperty(null, "mediaPlayer", settings.mediaPlayer);
        mediaPlayerParams = new SimpleStringProperty(null, "mediaPlayerParams", settings.mediaPlayerParams);
        browserOverride = new SimpleFileProperty(null, "browserOverride", settings.browserOverride);
        browserParams = new SimpleStringProperty(null, "browserParams", settings.browserParams);
        forceBrowserOverride = new SimpleBooleanProperty(null, "forceBrowserOverride", settings.forceBrowserOverride);
        maximumResolutionPlayer = new SimpleIntegerProperty(null, "maximumResolutionPlayer", settings.maximumResolutionPlayer);
        showPlayerStarting = new SimpleBooleanProperty(null, "showPlayerStarting", settings.showPlayerStarting);
        singlePlayer = new SimpleBooleanProperty(null, "singlePlayer", settings.singlePlayer);
        proxyType = new SimpleListProperty<>(null, "proxyType", FXCollections.observableList(List.of(DIRECT, HTTP, SOCKS4, SOCKS5)));
        proxyHost = new SimpleStringProperty(null, "proxyHost", settings.proxyHost);
        proxyPort = new SimpleStringProperty(null, "proxyPort", settings.proxyPort);
        proxyUser = new SimpleStringProperty(null, "proxyUser", settings.proxyUser);
        proxyPassword = new SimpleStringProperty(null, "proxyPassword", settings.proxyPassword);
        recordingsDir = new SimpleDirectoryProperty(null, "recordingsDir", settings.recordingsDir);
        directoryStructure = new SimpleListProperty<>(null, "recordingsDirStructure",
                FXCollections.observableList(List.of(FLAT, ONE_PER_MODEL, ONE_PER_GROUP, ONE_PER_RECORDING)));
        splitAfter = new SimpleListProperty<>(null, "splitRecordingsAfterSecs", FXCollections.observableList(getSplitAfterSecsOptions()));
        splitBiggerThan = new SimpleListProperty<>(null, "splitRecordingsBiggerThanBytes", FXCollections.observableList(getSplitBiggerThanOptions()));
        resolutionRange = new SimpleRangeProperty<>(rangeValues, "minimumResolution", "maximumResolution", settings.minimumResolution,
                settings.maximumResolution);
        concurrentRecordings = new SimpleIntegerProperty(null, "concurrentRecordings", settings.concurrentRecordings);
        onlineCheckIntervalInSecs = new SimpleIntegerProperty(null, "onlineCheckIntervalInSecs", settings.onlineCheckIntervalInSecs);
        leaveSpaceOnDevice = new SimpleLongProperty(null, "minimumSpaceLeftInBytes",
                (long) new GigabytesConverter().convertTo(settings.minimumSpaceLeftInBytes));
        ffmpegParameters = new SimpleStringProperty(null, "ffmpegMergedDownloadArgs", settings.ffmpegMergedDownloadArgs);
        logFFmpegOutput = new SimpleBooleanProperty(null, "logFFmpegOutput", settings.logFFmpegOutput);
        loghlsdlOutput = new SimpleBooleanProperty(null, "loghlsdlOutput", settings.loghlsdlOutput);
        logMissedSegments = new SimpleBooleanProperty(null, "logMissedSegments", settings.logMissedSegments);
        fileExtension = new SimpleStringProperty(null, "ffmpegFileSuffix", settings.ffmpegFileSuffix);
        server = new SimpleStringProperty(null, "httpServer", settings.httpServer);
        port = new SimpleIntegerProperty(null, "httpPort", settings.httpPort);
        path = new SimpleStringProperty(null, "servletContext", settings.servletContext);
        downloadFilename = new SimpleStringProperty(null, "downloadFilename", settings.downloadFilename);
        recordedModelsPerSite = new SimpleBooleanProperty(null, "recordedModelsPerSite", settings.recordedModelsPerSite);
        requireAuthentication = new SimpleBooleanProperty(null, "requireAuthentication", settings.requireAuthentication);
        requireAuthentication.addListener(this::requireAuthenticationChanged);
        totalModelCountInTitle = new SimpleBooleanProperty(null, "totalModelCountInTitle", settings.totalModelCountInTitle);
        showActiveRecordingsInTray = new SimpleBooleanProperty(null, "showActiveRecordingsInTray", settings.showActiveRecordingsInTray);
        transportLayerSecurity = new SimpleBooleanProperty(null, "transportLayerSecurity", settings.transportLayerSecurity);
        recordLocal = new ExclusiveSelectionProperty(null, "localRecording", settings.localRecording, "Local", "Remote");
        postProcessingThreads = new SimpleIntegerProperty(null, "postProcessingThreads", settings.postProcessingThreads);
        onlineCheckSkipsPausedModels = new SimpleBooleanProperty(null, "onlineCheckSkipsPausedModels", settings.onlineCheckSkipsPausedModels);
        fastScrollSpeed = new SimpleBooleanProperty(null, "fastScrollSpeed", settings.fastScrollSpeed);
        confirmationDialogs = new SimpleBooleanProperty(null, "confirmationForDangerousActions", settings.confirmationForDangerousActions);
        useHlsdl = new SimpleBooleanProperty(null, "useHlsdl", settings.useHlsdl);
        hlsdlExecutable = new SimpleFileProperty(null, "hlsdlExecutable", settings.hlsdlExecutable);
        recentlyWatched = new SimpleBooleanProperty(null, "recentlyWatched", settings.recentlyWatched);
        playlistRequestTimeout = new SimpleIntegerProperty(null, "playlistRequestTimeout", settings.playlistRequestTimeout);
        minimizeToTray = new SimpleBooleanProperty(null, "minimizeToTray", settings.minimizeToTray);
        startMinimized = new SimpleBooleanProperty(null, "startMinimized", settings.startMinimized);
        showGridLinesInTables = new SimpleBooleanProperty(null, "showGridLinesInTables", settings.showGridLinesInTables);
        defaultPriority = new SimpleIntegerProperty(null, "defaultPriority", settings.defaultPriority);
        timeoutRecordingStartingAt = new LocalTimeProperty(null, "timeoutRecordingStartingAt", settings.timeoutRecordingStartingAt);
        timeoutRecordingEndingAt = new LocalTimeProperty(null, "timeoutRecordingEndingAt", settings.timeoutRecordingEndingAt);
        recordUntilDefaultDurationInMinutes = new SimpleLongProperty(null, "recordUntilDefaultDurationInMinutes", settings.recordUntilDefaultDurationInMinutes);
        dateTimeFormat = new SimpleStringProperty(null, "dateTimeFormat", settings.dateTimeFormat);
        tabsSortable = new SimpleBooleanProperty(null, "tabsSortable", settings.tabsSortable);
        checkForUpdates = new SimpleBooleanProperty(null, "checkForUpdates", settings.checkForUpdates);
        filterBlacklist = new SimpleStringProperty(null, "filterBlacklist", settings.filterBlacklist);
        filterWhitelist = new SimpleStringProperty(null, "filterWhitelist", settings.filterWhitelist);
        deleteOrphanedRecordingMetadata = new SimpleBooleanProperty(null, "deleteOrphanedRecordingMetadata", settings.deleteOrphanedRecordingMetadata);
        restrictBitrate = new SimpleIntegerProperty(null, "restrictBitrate", settings.restrictBitrate);
        configSavingDelayMs = new SimpleIntegerProperty(null, "configSavingDelayMs", settings.configSavingDelayMs);
        httpClientMaxRequests = new SimpleIntegerProperty(null, "httpClientMaxRequests", settings.httpClientMaxRequests);
        httpClientMaxRequestsPerHost = new SimpleIntegerProperty(null, "httpClientMaxRequestsPerHost", settings.httpClientMaxRequestsPerHost);
    }

    private void createGui() {
        var postProcessingStepPanel = new PostProcessingStepPanel(config);
        var variablesHelpButton = createHelpButton("Variables", "http://localhost:5689/docs/PostProcessing.md#variables");
        ignoreList = new IgnoreList();
        List<Category> siteCategories = new ArrayList<>();
        for (Site site : sites) {
            ofNullable(SiteUiFactory.getUi(site)).map(SiteUI::getConfigUI).map(ConfigUI::createConfigPanel)
                    .ifPresent(configPanel -> siteCategories.add(Category.of(site.getName(), configPanel)));
        }

        var storage = new CtbrecPreferencesStorage(config);
        var prefs = Preferences.of(storage,
                Category.of("General",
                        Group.of("General",
                                Setting.of("User-Agent", httpUserAgent),
                                Setting.of("User-Agent mobile", httpUserAgentMobile),
                                Setting.of("Update overview interval (seconds)", overviewUpdateIntervalInSecs, "Update the thumbnail overviews every x seconds").needsRestart(),
                                Setting.of("Update thumbnails", updateThumbnails,
                                        "The overviews will still be updated, but the thumbnails won't be changed. This is useful for less powerful systems."),
                                Setting.of("Thumbnails cache size", new CacheSettingsPane(this, config)).needsRestart(),
                                Setting.of("Manually select stream quality", chooseStreamQuality, "Opens a dialog to select the video resolution before recording"),
                                Setting.of("Enable live previews (experimental)", livePreviews),
                                Setting.of("Enable recently watched tab", recentlyWatched).needsRestart(),
                                Setting.of("Minimize to tray", minimizeToTray, "Removes the app from the task bar, if minimized"),
                                Setting.of("Start minimized", startMinimized, "Start the app minimized to the tray, automatically activates \"Minimize to tray\""),
                                Setting.of("Add models from clipboard", monitorClipboard, "Monitor clipboard for model URLs and automatically add them to the recorder").needsRestart(),
                                Setting.of("Show confirmation dialogs", confirmationDialogs, "Show confirmation dialogs for irreversible actions"),
                                Setting.of("Recording tab per site", recordedModelsPerSite, "Add a Recording tab for each site").needsRestart(),
                                Setting.of("Check for new versions at startup", checkForUpdates, "Search for updates every startup"),
                                Setting.of("Start Tab", startTab)),

                        Group.of("Player",
                                Setting.of("Player", mediaPlayer),
                                Setting.of("Start parameters", mediaPlayerParams),
                                Setting.of("Maximum resolution (0 = unlimited)", maximumResolutionPlayer, "video height, e.g. 720 or 1080"),
                                Setting.of("Show \"Player Starting\" Message", showPlayerStarting),
                                Setting.of("Start only one player at a time", singlePlayer)),

                        Group.of("Browser",
                                Setting.of("Browser", browserOverride),
                                Setting.of("Start parameters", browserParams),
                                Setting.of("Force use (ignore default browser)", forceBrowserOverride, "Default behaviour will fallback to OS default if the above browser fails")),
                                
                        Group.of("Flaresolverr",
                                Setting.of("API URL", flaresolverrApiUrl),
                                Setting.of("Request timeout", flaresolverrTimeoutInMillis),
                                Setting.of("Use for domains (one per line)", flaresolverrUseForDomains))),
                Category.of("Look & Feel",
                        Group.of("Look & Feel",
                                Setting.of("Colors (Base / Accent)", new ColorSettingsPane(Config.getInstance())).needsRestart(),
                                Setting.of("Font", new FontSettingsPane(this, config)).needsRestart(),
                                Setting.of("Date format (empty = system default)", dateTimeFormat, DATE_FORMATTER_TOOLTIP).needsRestart(),
                                Setting.of("Display stream resolution in overview", determineResolution),
                                Setting.of("Total model count in title", totalModelCountInTitle, "Show the total number of models in the title bar"),
                                Setting.of("Show active recordings counter in tray", showActiveRecordingsInTray, "Show the number of running recorings in the tray icon"),
                                Setting.of("Show grid lines in tables", showGridLinesInTables, "Show grid lines in tables").needsRestart(),
                                Setting.of("Fast scroll speed", fastScrollSpeed, "Makes the thumbnail overviews scroll faster with the mouse wheel").needsRestart(),
                                Setting.of("Draggable tabs", tabsSortable, "Main tabs can be reordered").needsRestart())),
                Category.of("Recorder",
                        Group.of("Recorder",
                                Setting.of("Recordings Directory", recordingsDir),
                                Setting.of("Directory Structure", directoryStructure),
                                Setting.of("Split recordings after", splitAfter).converter(SplitAfterOption.converter()).onChange(this::splitValuesChanged),
                                Setting.of("Split recordings bigger than", splitBiggerThan).converter(SplitBiggerThanOption.converter()).onChange(this::splitValuesChanged),
                                Setting.of("Restrict Resolution", resolutionRange, "Only record streams with resolution within the given range"),
                                Setting.of("Restrict Resolution", new RestrictResolutionSidePane(config)),
                                Setting.of("Restrict Video Bitrate (kbps, 0 = unlimited)", restrictBitrate, "Only record streams with a video bitrate below this limit (kbps)"),
                                Setting.of("Concurrent Recordings (0 = unlimited)", concurrentRecordings),
                                Setting.of("Default Priority", defaultPriority, "lowest 0 - 10000 highest"),
                                Setting.of("Default duration for \"Record until\" (minutes)", recordUntilDefaultDurationInMinutes),
                                Setting.of("Leave space on device (GiB)", leaveSpaceOnDevice,
                                        "Stop recording, if the free space on the device gets below this threshold").converter(new GigabytesConverter()),
                                Setting.of("FFmpeg parameters", ffmpegParameters, "FFmpeg parameters to use when merging stream segments"),
                                Setting.of("File Extension", fileExtension, "File extension to use for recordings"),
                                Setting.of("Check online state every (seconds)", onlineCheckIntervalInSecs, "Check every x seconds, if a model came online"),
                                Setting.of("Skip online check for paused models", onlineCheckSkipsPausedModels, "Skip online check for paused models"),
                                Setting.of("Delete orphaned recording metadata", deleteOrphanedRecordingMetadata, "Delete recordings for which the video files are missing on start")),
                        Group.of("Timeout",
                                Setting.of("Don't record from", timeoutRecordingStartingAt),
                                Setting.of("Until", timeoutRecordingEndingAt)
                        ),
                        Group.of("Location",
                                Setting.of("Record Location", recordLocal).needsRestart(),
                                Setting.of("Server", server),
                                Setting.of("Port", port),
                                Setting.of("Path", path, "Leave empty, if you didn't change the servletContext in the server config"),
                                Setting.of("Download Filename", downloadFilename, "File name pattern for downloads"),
                                Setting.of("", variablesHelpButton),
                                Setting.of("Require authentication", requireAuthentication),
                                Setting.of("Use Secure Communication (TLS)", transportLayerSecurity))),
                Category.of("Post-Processing",
                        Group.of("Post-Processing",
                                Setting.of("Threads", postProcessingThreads),
                                Setting.of("Steps", postProcessingStepPanel),
                                Setting.of("", createHelpButton("Post-Processing Help", "http://localhost:5689/docs/PostProcessing.md")),
                                Setting.of("", createVariablePlayGroundButton()))),
                Category.of("Events & Actions", new ActionSettingsPanel(recorder)),
                Category.of("Filtering",
                        Group.of("Ignore List",
                                Setting.of("", ignoreList)),
                        Group.of("Text Filters",
                                Setting.of("Blacklist", filterBlacklist, "Default list of blacklist filters for site views, space seperated"),
                                Setting.of("Whitelist", filterWhitelist, "Default list of whitelist filters for site views, space seperated"))), Category.of("Sites", siteCategories.toArray(new Category[0])),
                Category.of("Proxy",
                        Group.of("Proxy",
                                Setting.of("Type", proxyType).needsRestart(),
                                Setting.of("Host", proxyHost).needsRestart(),
                                Setting.of("Port", proxyPort).needsRestart(),
                                Setting.of("Username", proxyUser).needsRestart(),
                                Setting.of("Password", proxyPassword).needsRestart())),
                Category.of("Advanced / Devtools",
                        Group.of("Networking",
                                Setting.of("Playlist request timeout (ms)", playlistRequestTimeout, "Timeout in ms for playlist requests"),
                                Setting.of("Max requests", httpClientMaxRequests, 
                                                "The maximum number of requests to execute concurrently. Above this requests queue in memory,\n" + //
                                                "waiting for the running calls to complete.\n\n" + //
                                                "If more than [maxRequests] requests are in flight when this is invoked, those requests will remain in flight."),
                                Setting.of("Max requests per host", httpClientMaxRequestsPerHost, 
                                                "The maximum number of requests for each host to execute concurrently. This limits requests by\n" + //
                                                "the URL's host name. Note that concurrent requests to a single IP address may still exceed this\n" + //
                                                "limit: multiple hostnames may share an IP address or be routed through the same HTTP proxy.\n\n" + //
                                                "If more than [maxRequestsPerHost] requests are in flight when this is invoked, those requests will remain in flight.\n\n" + //
                                                "WebSocket connections to hosts **do not** count against this limit.")),
                        Group.of("Logging",
                                Setting.of("Log FFmpeg output", logFFmpegOutput, "Log FFmpeg output to files in the system's temp directory"),
                                Setting.of("Log missed segments", logMissedSegments,
                                        "Write a log files in the system's temp directory to analyze missed segments")),
                        Group.of("hlsdl (experimental)",
                                Setting.of("Use hlsdl (if possible)", useHlsdl,
                                        "Use hlsdl to record the live streams. Some features might not work correctly."),
                                Setting.of("hlsdl executable", hlsdlExecutable, "Path to the hlsdl executable"),
                                Setting.of("Log hlsdl output", loghlsdlOutput, "Log hlsdl output to files in the system's temp directory")),
                        Group.of("Miscelaneous",
                                Setting.of("Config file saving delay (ms)", configSavingDelayMs, 
                                        "Wait specified number of milliseconds before actually writing config to disk"))));
        Region preferencesView = prefs.getView();
        prefs.onRestartRequired(this::showRestartRequired);
        storage.setPreferences(prefs);

        var stackPane = new StackPane();
        stackPane.getChildren().add(preferencesView);
        restartNotification = new Label("Restart Required");
        restartNotification.setVisible(false);
        restartNotification.setOpacity(0);
        restartNotification.setStyle("-fx-font-size: 28; -fx-padding: .3em");
        restartNotification
                .setBorder(new Border(new BorderStroke(Color.web(settings.colorAccent), BorderStrokeStyle.SOLID, new CornerRadii(5), new BorderWidths(2))));
        restartNotification.setBackground(new Background(new BackgroundFill(Color.web(settings.colorBase), new CornerRadii(5), Insets.EMPTY)));
        stackPane.getChildren().add(restartNotification);
        StackPane.setAlignment(restartNotification, Pos.TOP_RIGHT);
        StackPane.setMargin(restartNotification, new Insets(10, 40, 0, 0));

        setContent(stackPane);
        prefs.expandTree();

        prefs.getSetting("flaresolverr.apiUrl").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("flaresolverr.timeoutInMillis").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("httpServer").ifPresent(s -> bindEnabledProperty(s, recordLocal));
        prefs.getSetting("httpPort").ifPresent(s -> bindEnabledProperty(s, recordLocal));
        prefs.getSetting("servletContext").ifPresent(s -> bindEnabledProperty(s, recordLocal));
        prefs.getSetting("requireAuthentication").ifPresent(s -> bindEnabledProperty(s, recordLocal));
        prefs.getSetting("transportLayerSecurity").ifPresent(s -> bindEnabledProperty(s, recordLocal));
        prefs.getSetting("recordingsDir").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("splitRecordingsAfterSecs").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("splitRecordingsBiggerThanBytes").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("minimumResolution").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("recordingsDirStructure").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("onlineCheckIntervalInSecs").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("onlineCheckSkipsPausedModels").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("minimumSpaceLeftInBytes").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("postProcessing").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("postProcessingThreads").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("removeRecordingAfterPostProcessing").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("minimumLengthInSeconds").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("concurrentRecordings").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("timeoutRecordingStartingAt").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("timeoutRecordingEndingAt").ifPresent(s -> bindEnabledProperty(s, recordLocal.not()));
        prefs.getSetting("downloadFilename").ifPresent(s -> bindEnabledProperty(s, recordLocal));
        prefs.getSetting("hlsdlExecutable").ifPresent(s -> bindEnabledProperty(s, useHlsdl.not()));
        prefs.getSetting("loghlsdlOutput").ifPresent(s -> bindEnabledProperty(s, useHlsdl.not()));
        postProcessingStepPanel.disableProperty().bind(recordLocal.not());
        variablesHelpButton.disableProperty().bind(recordLocal);
    }

    private void splitValuesChanged(ObservableValue<?> value, Object oldV, Object newV) {
        boolean splitAfterSet = settings.splitRecordingsAfterSecs > 0;
        boolean splitBiggerThanSet = settings.splitRecordingsBiggerThanBytes > 0;
        if (splitAfterSet && splitBiggerThanSet) {
            settings.splitStrategy = TIME_OR_SIZE;
        } else if (splitAfterSet) {
            settings.splitStrategy = TIME;
        } else if (splitBiggerThanSet) {
            settings.splitStrategy = SIZE;
        } else {
            settings.splitStrategy = DONT;
        }
        saveConfig();
    }

    private Button createHelpButton(String text, String url) {
        var postProcessingHelpButton = new Button(text);
        postProcessingHelpButton.setOnAction(e -> {
            new Thread(() -> {
                try {
                    DocServer.start();
                } catch (Exception ex) {
                    LOG.error("Couldn't start documentation server", ex);
                }
            }).start();
            DesktopIntegration.open(url);
        });
        return postProcessingHelpButton;
    }

    private Button createVariablePlayGroundButton() {
        var button = new Button("Variable Playground");
        button.setOnAction(e -> variablePlayGroundDialogFactory.openDialog(this.getTabPane().getScene(), config, recorder));
        return button;
    }

    private void bindEnabledProperty(Setting s, BooleanExpression bindTo) {
        try {
            s.getGui().disableProperty().bind(bindTo);
        } catch (Exception e) {
            LOG.error("Couldn't bind disableProperty of {}", s.getName(), e);
        }
    }

    private List<String> getTabNames() {
        return getTabPane().getTabs().stream().map(Tab::getText).toList();
    }

    private List<SplitAfterOption> getSplitAfterSecsOptions() {
        List<SplitAfterOption> splitOptions = new ArrayList<>();
        splitOptions.add(new SplitAfterOption("disabled", 0));
        if (Config.isDevMode()) {
            splitOptions.add(new SplitAfterOption("1 min", 60));
            splitOptions.add(new SplitAfterOption("3 min", 3 * 60));
        }
        splitOptions.add(new SplitAfterOption("5 min", 5 * 60));
        splitOptions.add(new SplitAfterOption("10 min", 10 * 60));
        splitOptions.add(new SplitAfterOption("15 min", 15 * 60));
        splitOptions.add(new SplitAfterOption("20 min", 20 * 60));
        splitOptions.add(new SplitAfterOption("30 min", 30 * 60));
        splitOptions.add(new SplitAfterOption("60 min", 60 * 60));
        return splitOptions;
    }

    private List<SplitBiggerThanOption> getSplitBiggerThanOptions() {
        List<SplitBiggerThanOption> splitOptions = new ArrayList<>();
        splitOptions.add(new SplitBiggerThanOption("disabled", 0));
        if (Config.isDevMode()) {
            splitOptions.add(new SplitBiggerThanOption("10 MiB", 10 * MiB));
            splitOptions.add(new SplitBiggerThanOption("20 MiB", 20 * MiB));
        }
        splitOptions.add(new SplitBiggerThanOption("100 MiB", 100 * MiB));
        splitOptions.add(new SplitBiggerThanOption("250 MiB", 250 * MiB));
        splitOptions.add(new SplitBiggerThanOption("500 MiB", 500 * MiB));
        splitOptions.add(new SplitBiggerThanOption("1 GiB", GiB));
        splitOptions.add(new SplitBiggerThanOption("2 GiB", 2 * GiB));
        splitOptions.add(new SplitBiggerThanOption("3 GiB", 3 * GiB));
        splitOptions.add(new SplitBiggerThanOption("4 GiB", 4 * GiB));
        splitOptions.add(new SplitBiggerThanOption("5 GiB", 5 * GiB));
        splitOptions.add(new SplitBiggerThanOption("6 GiB", 6 * GiB));
        splitOptions.add(new SplitBiggerThanOption("7 GiB", 7 * GiB));
        splitOptions.add(new SplitBiggerThanOption("8 GiB", 8 * GiB));
        splitOptions.add(new SplitBiggerThanOption("9 GiB", 9 * GiB));
        splitOptions.add(new SplitBiggerThanOption("10 GiB", 10 * GiB));
        return splitOptions;
    }

    private void requireAuthenticationChanged(ObservableValue<?> obs, Boolean oldV, Boolean newV) { // NOSONAR
        boolean requiresAuthentication = newV;
        Config.getInstance().getSettings().requireAuthentication = requiresAuthentication;
        if (requiresAuthentication) {
            byte[] key = Config.getInstance().getSettings().key;
            if (key == null) {
                key = Hmac.generateKey();
                Config.getInstance().getSettings().key = key;
                saveConfig();
            }
            var keyDialog = new TextInputDialog();
            keyDialog.setResizable(true);
            keyDialog.setTitle("Server Authentication");
            keyDialog.setHeaderText("A key has been generated");
            keyDialog.setContentText("Add this setting to your server's config.json:\n");
            keyDialog.getEditor().setText("\"key\": " + Arrays.toString(key));
            keyDialog.getEditor().setEditable(false);
            keyDialog.setWidth(800);
            keyDialog.setHeight(200);
            keyDialog.show();
        }
    }

    public void saveConfig() {
        GlobalThreadPool.submit(() -> {
            try {
                Config.getInstance().save();
            } catch (IOException e) {
                LOG.error("Couldn't save config", e);
            }
        });
    }

    @Override
    public void selected() {
        if (!initialized) {
            initializeProperties();
            createGui();
            initialized = true;
        }
        ignoreList.refresh();
    }

    @Override
    public void deselected() {
        saveConfig();
    }

    public static GridPane createGridLayout() {
        var layout = new GridPane();
        layout.setPadding(new Insets(10));
        layout.setHgap(5);
        layout.setVgap(5);
        return layout;
    }

    void showRestartRequired() {
        if (!restartNotification.isVisible()) {
            restartNotification.setVisible(true);
            Transition fadeIn = changeOpacity(restartNotification, 1);
            fadeIn.play();
            fadeIn.setOnFinished(e -> {
                Transition fadeOut = changeOpacity(restartNotification, 0);
                fadeOut.setOnFinished(e2 -> restartNotification.setVisible(false));
                var pauseTransition = new PauseTransition(Duration.seconds(5));
                pauseTransition.setOnFinished(evt -> fadeOut.play());
                pauseTransition.play();
            });
        }
    }

    private static final Duration ANIMATION_DURATION = new Duration(500);

    private Transition changeOpacity(Node node, double opacity) {
        var transition = new FadeTransition(ANIMATION_DURATION, node);
        transition.setFromValue(node.getOpacity());
        transition.setToValue(opacity);
        return transition;
    }

    public record SplitAfterOption(String label, @Getter int value) {

        @Override
        public String toString() {
            return label;
        }

        @Override
        public int hashCode() {
            final var prime = 31;
            var result = 1;
            result = prime * result + value;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SplitAfterOption other = (SplitAfterOption) obj;
            return value == other.value;
        }

        public static ValueConverter converter() {
            return new ValueConverter() {
                @Override
                public Integer convertFrom(Object splitAfterOption) {
                    return ((SplitAfterOption) splitAfterOption).getValue();
                }

                @Override
                public SplitAfterOption convertTo(Object integer) {
                    return new SplitAfterOption(integer.toString(), (Integer) integer);
                }
            };
        }
    }

    public record SplitBiggerThanOption(String label, @Getter long value) {

        @Override
        public String toString() {
            return label;
        }

        @Override
        public int hashCode() {
            final var prime = 31;
            var result = 1;
            result = prime * result + (int) (value ^ (value >>> 32));
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SplitBiggerThanOption other = (SplitBiggerThanOption) obj;
            return value == other.value;
        }

        public static ValueConverter converter() {
            return new ValueConverter() {
                @Override
                public Long convertFrom(Object splitBiggerThanOption) {
                    return ((SplitBiggerThanOption) splitBiggerThanOption).getValue();
                }

                @Override
                public SplitBiggerThanOption convertTo(Object value) {
                    return new SplitBiggerThanOption(value.toString(), (Long) value);
                }
            };
        }
    }

    private static final String DATE_FORMATTER_TOOLTIP = """
              Leave empty for system default
              
              Symbol  Meaning                     Presentation      Examples
              ------  -------                     ------------      -------
               G       era                         text              AD; Anno Domini; A
               u       year                        year              2004; 04
               y       year-of-era                 year              2004; 04
               D       day-of-year                 number            189
               M/L     month-of-year               number/text       7; 07; Jul; July; J
               d       day-of-month                number            10
                        
               Q/q     quarter-of-year             number/text       3; 03; Q3; 3rd quarter
               Y       week-based-year             year              1996; 96
               w       week-of-week-based-year     number            27
               W       week-of-month               number            4
               E       day-of-week                 text              Tue; Tuesday; T
               e/c     localized day-of-week       number/text       2; 02; Tue; Tuesday; T
               F       week-of-month               number            3
                        
               a       am-pm-of-day                text              PM
               h       clock-hour-of-am-pm (1-12)  number            12
               K       hour-of-am-pm (0-11)        number            0
               k       clock-hour-of-am-pm (1-24)  number            0
                        
               H       hour-of-day (0-23)          number            0
               m       minute-of-hour              number            30
               s       second-of-minute            number            55
               S       fraction-of-second          fraction          978
               A       milli-of-day                number            1234
               n       nano-of-second              number            987654321
               N       nano-of-day                 number            1234000000
                        
               V       time-zone ID                zone-id           America/Los_Angeles; Z; -08:30
               z       time-zone name              zone-name         Pacific Standard Time; PST
               O       localized zone-offset       offset-O          GMT+8; GMT+08:00; UTC-08:00;
               X       zone-offset 'Z' for zero    offset-X          Z; -08; -0830; -08:30; -083015; -08:30:15;
               x       zone-offset                 offset-x          +0000; -08; -0830; -08:30; -083015; -08:30:15;
               Z       zone-offset                 offset-Z          +0000; -0800; -08:00;
                        
               p       pad next                    pad modifier      1
                        
               '       escape for text             delimiter
               ''      single quote                literal           '
               [       optional section start
               ]       optional section end
               #       reserved for future use
               {       reserved for future use
               }       reserved for future use
            """;
}
