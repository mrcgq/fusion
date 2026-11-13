package ctbrec;

import ctbrec.event.EventHandlerConfiguration;
import ctbrec.io.json.dto.ModelDto;
import ctbrec.io.json.dto.PostProcessorDto;

import java.io.File;
import java.time.LocalTime;
import java.util.*;

public class Settings {

    public enum DirectoryStructure {
        FLAT("all recordings in one directory"),
        ONE_PER_MODEL("one directory for each model"),
        ONE_PER_GROUP("one directory for each group"),
        ONE_PER_RECORDING("one directory for each recording");

        private final String description;

        DirectoryStructure(String description) {
            this.description = description;
        }

        @Override
        public String toString() {
            return description;
        }
    }

    public enum ProxyType {
        DIRECT,
        HTTP,
        SOCKS4,
        SOCKS5
    }

    public enum SplitStrategy {
        DONT,
        TIME,
        SIZE,
        TIME_OR_SIZE
    }

    public class FlaresolverrSettings {
        public String apiUrl = "http://localhost:8191/v1";
        public int timeoutInMillis = 60000;
        public String userAgent = "";
        public List<String> useForDomains = new ArrayList<>(); //(List.of("chaturbate.com", "bongacams.com"));
    };

    public FlaresolverrSettings flaresolverr = new FlaresolverrSettings();
    public String amateurTvUsername = "";
    public String amateurTvPassword = "";
    public String bongacamsBaseUrl = "https://bongacams.com";
    public String bongaPassword = "";
    public String bongaUsername = "";
    public String cam4Password = "";
    public String cam4Username = "";
    public String camsodaPassword = "";
    public String camsodaUsername = "";
    public String chaturbatePassword = "";
    public String chaturbateUsername = "";
    public String chaturbateBaseUrl = "https://chaturbate.com";
    @Deprecated
    public boolean chaturbateUseFlaresolverr = false;
    public int chaturbateMsBetweenRequests = 1000;
    public String cherryTvPassword = "";
    public String cherryTvUsername = "";
    public boolean chooseStreamQuality = false;
    public String colorAccent = "#FFFFFF";
    public String colorBase = "#FFFFFF";
    public int concurrentRecordings = 0;
    public boolean confirmationForDangerousActions = false;
    public String contactsheetTimestampLook = "font=sans-serif:fontcolor=white:fontsize=60:box=1:boxcolor=black@0.5:boxborderw=5";
    public String dateTimeFormat = "";
    public int defaultPriority = 50;
    public boolean deleteOrphanedRecordingMetadata = false;
    public boolean determineResolution = false;
    public List<String> disabledSites = new ArrayList<>();
    public String downloadFilename = "$sanitize(${modelName})_$format(${localDateTime})";
    public List<EventHandlerConfiguration> eventHandlers = new ArrayList<>();
    public boolean eventsSuspended = false;
    public boolean fastScrollSpeed = true;
    public String fc2livePassword = "";
    public String fc2liveUsername = "";
    public String ffmpegMergedDownloadArgs = "-c:v copy -c:a copy -movflags faststart -y -f mpegts";
    public String ffmpegFileSuffix = "ts";
    public String flirt4freePassword;
    public String flirt4freeUsername;
    public String fontFamily = "Sans-Serif";
    public int fontSize = 14;
    public String hlsdlExecutable = "hlsdl";
    public int httpPort = 8080;
    public int httpSecurePort = 8443;
    public String httpServer = "localhost";
    public int httpTimeout = 10000;
    public String httpUserAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:105.0) Gecko/20100101 Firefox/105.0";
    public String httpUserAgentMobile = "Mozilla/5.0 (Android 9.0; Mobile; rv:105.0) Gecko/20100101 Firefox/105.0";
    public byte[] key = null;
    public List<String> ignoredModels = new ArrayList<>();
    public String lastDownloadDir = "";
    public String livejasminBaseUrl = "https://www.livejasmin.com";
    public boolean livejasminBetaAcknowledged = false;
    public String livejasminPassword = "";
    public String livejasminUsername = "";
    public boolean livePreviews = false;
    public boolean localRecording = true;
    public boolean logFFmpegOutput = false;
    public boolean loghlsdlOutput = false;
    public boolean logMissedSegments = false;
    public int minimumResolution = 0;
    public int maximumResolution = 8640;
    public int maximumResolutionPlayer = 0;
    public String mediaPlayer = "/usr/bin/mpv";
    public String mediaPlayerParams = "";
    public String browserOverride = "";
    public String browserParams = "";
    public boolean forceBrowserOverride = false;
    public String mfcBaseUrl = "https://www.myfreecams.com";
    public List<String> mfcDisabledModelsTableColumns = new ArrayList<>();
    public String[] mfcModelsTableColumnIds = new String[0];
    public double[] mfcModelsTableColumnWidths = new double[0];
    public String mfcModelsTableFilter = "";
    public String mfcModelsTableSortColumn = "";
    public String mfcModelsTableSortType = "";
    public String mfcPassword = "";
    public String mfcUsername = "";
    public boolean minimizeToTray = false;
    @Deprecated
    public int minimumLengthInSeconds = 0;
    public long minimumSpaceLeftInBytes = 0;
    public List<ModelDto> models = new ArrayList<>();
    public Set<ModelGroup> modelGroups = new HashSet<>();
    public Map<String, String> modelNotes = new HashMap<>();
    public Map<String, String> modelPortraits = new HashMap<>();
    @Deprecated
    public List<ModelDto> modelsIgnored = new ArrayList<>();
    public boolean monitorClipboard = false;
    public int onlineCheckIntervalInSecs = 60;
    public boolean onlineCheckSkipsPausedModels = false;
    public int overviewUpdateIntervalInSecs = 10;
    @Deprecated
    public String password = "";
    @Deprecated
    public String postProcessing = "";
    public int playlistRequestTimeout = 2000;
    public int httpClientMaxRequests = 64;
    public int httpClientMaxRequestsPerHost = 16;
    public int postProcessingThreads = 2;
    public List<PostProcessorDto> postProcessors = new ArrayList<>();
    public String proxyHost;
    public String proxyPassword;
    public String proxyPort;
    public ProxyType proxyType = ProxyType.DIRECT;
    public String proxyUser;
    public boolean recentlyWatched = true;
    public List<String> recordLaterTableColumnOrder = new ArrayList<>();
    public Map<String, Boolean> recordLaterTableColumnVisibility = new HashMap<>();
    public Map<String, Double> recordLaterTableColumnWidth = new HashMap<>();
    public String recordLaterTableSortColumn = "";
    public String recordLaterTableSortType = "";
    public List<String> recordedModelsTableColumnOrder = new ArrayList<>();
    public Map<String, Boolean> recordedModelsTableColumnVisibility = new HashMap<>();
    public Map<String, Double> recordedModelsTableColumnWidth = new HashMap<>();
    public String recordedModelsTableSortColumn = "";
    public boolean recordedModelsPerSite = false;
    public String recordedModelsTableSortType = "";
    public List<String> recordingsTableColumnOrder = new ArrayList<>();
    public Map<String, Boolean> recordingsTableColumnVisibility = new HashMap<>();
    public Map<String, Double> recordingsTableColumnWidth = new HashMap<>();
    public String recordingsTableSortColumn = "";
    public String recordingsTableSortType = "";
    public String recordingsDir = System.getProperty("user.home") + File.separator + "ctbrec";
    public DirectoryStructure recordingsDirStructure = DirectoryStructure.FLAT;
    public List<ModelDto> recordLater = new ArrayList<>();
    public boolean recordSingleFile = false;
    public long recordUntilDefaultDurationInMinutes = 24 * 60L;
    public boolean removeRecordingAfterPostProcessing = false;
    public boolean requireAuthentication = false;
    public int segmentErrorMeasurePeriodInSecs = 20;
    public int segmentErrorThresholdToStopRecording = 5;
    public String servletContext = "";
    public boolean showActiveRecordingsInTray = true;
    public String showActiveRecordingsInTrayColor = "#FFFFFF";
    public String showActiveRecordingsInTrayFont = "SansSerif";
    public int showActiveRecordingsInTrayFontSize = 48;
    public boolean showGridLinesInTables = true;
    public boolean showPlayerStarting = false;
    public String showupUsername = "";
    public String showupPassword = "";
    public boolean singlePlayer = true;
    @Deprecated
    public int splitRecordings = 0;
    public SplitStrategy splitStrategy = SplitStrategy.DONT;
    public int splitRecordingsAfterSecs = 0;
    public long splitRecordingsBiggerThanBytes = 0;
    public String startTab = "Settings";
    public boolean startMinimized = false;
    public String streamatePassword = "";
    public String streamateUsername = "";
    public String stripchatUsername = "";
    public String stripchatPassword = "";
    public boolean stripchatUseXhamster = false;
    public List<String> tabOrder = new ArrayList<>();
    public boolean tabsSortable = true;
    public LocalTime timeoutRecordingStartingAt = LocalTime.of(0, 0);
    public LocalTime timeoutRecordingEndingAt = LocalTime.of(0, 0);
    public boolean totalModelCountInTitle = false;
    public boolean transportLayerSecurity = true;
    public int thumbWidth = 180;
    public boolean updateThumbnails = true;
    public boolean useHlsdl = false;
    @Deprecated
    public String username = "";
    public int windowHeight = 800;
    public boolean windowMaximized = false;
    public int windowWidth = 1340;
    public int windowX;
    public int windowY;
    public boolean webinterface = false;
    public String webinterfaceUsername = "ctbrec";
    public String webinterfacePassword = "sucks";
    public String xlovecamUsername = "";
    public String xlovecamPassword = "";
    public boolean stripchatVR = false;
    public String xorKeyV2 = "t0cYOap4Ty1l9jzb";
    public String v1Key1 = "NTK9aqcLmNFMWrpQ";
    public String v1Key2 = "t0cYOap4Ty1l9jzb";
    public boolean stripchatDemangle = true;
    public boolean streamrayRecordGoalShows = false;
    public boolean checkForUpdates = true;
    public int thumbCacheSize = 16;
    public boolean dreamcamVR = false;
    public String filterBlacklist = "";
    public String filterWhitelist = "";
    public boolean checkResolutionByMinSide = false;
    public int restrictBitrate = 0;
    public int configSavingDelayMs = 400;
}
