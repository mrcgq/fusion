package ctbrec;


import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.event.ModelPredicate;
import ctbrec.io.IoUtils;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.sites.Site;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardOpenOption.*;

// TODO don't use singleton pattern
@Slf4j
public class Config {

    private static final String SYSPROP_CONFIG_DIR = "ctbrec.config.dir";
    private static final String V_4_7_5 = "4.7.5";


    private static Config instance;
    private Settings settings;
    private final String filename;
    private final List<Site> sites;
    private final File configDir;
    /**
     * to temporarily disable saving of the config
     * this is useful for the SettingsTab, because setting the initial values of some components causes an immediate save
     */
    private boolean savingDisabled = false;
    public static final String RECORDING_DATE_FORMAT = "yyyy-MM-dd_HH-mm-ss_SSS";

    private final ObjectMapper mapper = ObjectMapperFactory.getMapper();

    public Config(List<Site> sites) throws IOException {
        this.sites = sites;

        copyConfigIfNewVersion();

        if (System.getProperty(SYSPROP_CONFIG_DIR) != null) {
            configDir = new File(System.getProperty(SYSPROP_CONFIG_DIR), Version.getVersion().toString());
        } else {
            configDir = new File(OS.getConfigDir(), Version.getVersion().toString());
        }

        backupConfig(configDir);

        if (System.getProperty("ctbrec.config") != null) {
            filename = System.getProperty("ctbrec.config");
        } else {
            filename = "settings.json";
        }
    }

    private void backupConfig(File currentConfigDir) throws IOException {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(RECORDING_DATE_FORMAT);
        File src = currentConfigDir;
        if (src.exists()) {
            File target = new File(src.getParentFile(), src.getName() + "_backup_" + dateTimeFormatter.format(LocalDateTime.now()));
            log.info("Creating a backup of the config {} in {}", src, target);
            FileUtils.copyDirectory(src, target, this::includeDir, true);
            deleteOldBackups(currentConfigDir);
        }
    }

    private boolean includeDir(File pathname) {
        String name = pathname.getName();
        if (name.contains("minimal-browser") && name.contains("Cache")) return false;
        if (name.contains("cache")) return false;
        return true;
    }

    private void deleteOldBackups(File currentConfigDir) {
        File parent = currentConfigDir.getParentFile();
        File[] backupDirectories = parent.listFiles(file -> file.isDirectory() && file.getName().matches(Pattern.quote(currentConfigDir.getName()) + "_backup_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_\\d{3}"));
        Arrays.sort(backupDirectories, Comparator.comparing(File::getName));
        for (int i = 0; i < backupDirectories.length - 5; i++) {
            File dirToDelete = backupDirectories[i];
            try {
                log.info("Delete old config backup {}", dirToDelete);
                IoUtils.deleteDirectory(dirToDelete);
            } catch (IOException e) {
                log.error("Couldn't delete old config backup {}. You might have to delete it manually.", dirToDelete, e);
            }
        }
    }

    private void copyConfigIfNewVersion() throws IOException {
        File configDirectory;
        if (System.getProperty(SYSPROP_CONFIG_DIR) != null) {
            configDirectory = new File(System.getProperty(SYSPROP_CONFIG_DIR));
        } else {
            configDirectory = OS.getConfigDir();
        }

        Version currentVersion = Version.getVersion();
        Version previousVersion = getPreviousVersion(configDirectory);

        File src;
        File target = new File(configDirectory, currentVersion.toString());
        if (target.exists()) {
            return;
        }

        if (previousVersion.compareTo(Version.of(V_4_7_5)) <= 0) {
            src = configDirectory;
        } else {
            src = new File(configDirectory, previousVersion.toString());
        }
        if (!src.exists()) {
            // new installation
            return;
        }
        if (!Objects.equals(previousVersion, currentVersion)) {
            log.debug("Version update {} -> {}", previousVersion, currentVersion);
            log.debug("Copying config from {} to {}", src, target);
            FileUtils.copyDirectory(src, target, pathname -> !(pathname.toString().contains("minimal-browser") && pathname.toString().contains("Cache")), true);
        }
    }

    private Version getPreviousVersion(File configDirectory) {
        File[] versionDirectories = configDirectory.listFiles((dir, name) -> name.matches("\\d+\\.\\d+\\.\\d+"));
        if (versionDirectories != null) {
            Optional<Version> previousVersion = Arrays.stream(versionDirectories)
                    .map(File::getName)
                    .map(Version::of)
                    .max(Comparator.naturalOrder());
            return previousVersion.orElse(Version.of(V_4_7_5));
        } else {
            return Version.of(V_4_7_5);
        }
    }

    private void load() throws IOException {
        File configFile = new File(configDir, filename);
        log.info("Loading config from {}", configFile.getAbsolutePath());
        if (configFile.exists()) {
            try {
                byte[] fileContent = Files.readAllBytes(configFile.toPath());
                if (fileContent[0] == -17 && fileContent[1] == -69 && fileContent[2] == -65) {
                    // found BOM (byte order mark)
                    log.debug("Removing BOM from config file");
                    fileContent[0] = ' ';
                    fileContent[1] = ' ';
                    fileContent[2] = ' ';
                }
                String json = new String(fileContent, UTF_8).trim();
                json = migrateJson(json);
                settings = Objects.requireNonNull(mapper.readValue(json, Settings.class));
                settings.httpTimeout = Math.max(settings.httpTimeout, 10_000);
                if (settings.recordingsDir.endsWith("/")) {
                    settings.recordingsDir = settings.recordingsDir.substring(0, settings.recordingsDir.length() - 1);
                }
            } catch (Exception e) {
                settings = OS.getDefaultSettings();
                for (Site site : sites) {
                    site.setEnabled(!settings.disabledSites.contains(site.getName()));
                }
                throw e;
            }
        } else {
            log.error("Config file does not exist. Falling back to default values.");
            settings = OS.getDefaultSettings();
        }
        for (Site site : sites) {
            site.setEnabled(!settings.disabledSites.contains(site.getName()));
        }

        migrateOldSettings();
    }

    private String migrateJson(String jsonStr) {
        var json = new JSONObject(jsonStr);
        migrateTo5_1_2(json);
        migrateTo5_3_2(json);
        return json.toString();
    }

    private void migrateTo5_1_2(JSONObject json) {
        JSONObject s = json;

        if (s.has("models")) {
            JSONArray models = s.getJSONArray("models");
            for (int i = 0; i < models.length(); i++) {
                MigrateModel5_1_2.migrate(models.getJSONObject(i));
            }
        }

        if (s.has("eventHandlers")) {
            JSONArray eventHandlers = s.getJSONArray("eventHandlers");
            for (int i = 0; i < eventHandlers.length(); i++) {
                JSONObject eventHandler = eventHandlers.getJSONObject(i);
                if (eventHandler.has("predicates")) {
                    JSONArray predicates = eventHandler.getJSONArray("predicates");
                    for (int j = 0; j < predicates.length(); j++) {
                        JSONObject predicate = predicates.getJSONObject(j);
                        if (Objects.equals(ModelPredicate.class.getName(), predicate.optString("type"))) {
                            JSONArray models = predicate.getJSONArray("models");
                            for (int k = 0; k < models.length(); k++) {
                                JSONObject model = models.getJSONObject(k);
                                MigrateModel5_1_2.migrate(model);
                            }
                        }
                    }
                }
            }
        }
    }
    
    private void migrateTo5_3_2(JSONObject json) {
        if (json.has("chaturbateUseFlaresolverr") && json.has("flaresolverr")) {
            var fsr = json.getJSONObject("flaresolverr");
            
            if (!fsr.has("useForDomains") && json.getBoolean("chaturbateUseFlaresolverr")) {
                fsr.put("useForDomains", new JSONArray().put("chaturbate.com"));
            }
        }
    }

    private void migrateOldSettings() {
    }

    public static synchronized void init(List<Site> sites) throws IOException {
        if (instance == null) {
            instance = new Config(sites);
            instance.load();
        }
    }

    public static synchronized Config getInstance() {
        if (instance == null) {
            throw new IllegalStateException("Config not initialized, call init() first");
        }
        return instance;
    }

    public Settings getSettings() {
        return settings;
    }

    public synchronized void save() throws IOException {
        if (savingDisabled) {
            return;
        }
        String json = mapper.writeValueAsString(settings);
        File configFile = new File(configDir, filename);
        log.debug("Saving config to {}", configFile.getAbsolutePath());
        Files.createDirectories(configDir.toPath());
        Files.writeString(configFile.toPath(), json, CREATE, WRITE, TRUNCATE_EXISTING);
    }

    public static boolean isServerMode() {
        return Objects.equals(System.getProperty("ctbrec.server.mode"), "1");
    }

    public static boolean isDevMode() {
        return Objects.equals(System.getenv("CTBREC_DEV"), "1");
    }

    public File getConfigDir() {
        return configDir;
    }

    public File getFileForRecording(Model model, String suffix, Instant startTime) {
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(RECORDING_DATE_FORMAT);
        LocalDateTime startDateTime = LocalDateTime.ofInstant(startTime, ZoneId.systemDefault());
        String formattedDate = dateTimeFormatter.format(startDateTime);
        File dirForRecording = getDirForRecording(model, formattedDate);
        File targetFile = new File(dirForRecording, model.getSanitizedNamed() + '_' + formattedDate + '.' + suffix);
        return targetFile;
    }

    private File getDirForRecording(Model model, String formattedDate) {
        switch (getSettings().recordingsDirStructure) {
            case ONE_PER_MODEL -> {
                return new File(getSettings().recordingsDir, model.getSanitizedNamed());
            }
            case ONE_PER_GROUP -> {
                return new File(getSettings().recordingsDir, getGroupOrModelName(model));
            }
            case ONE_PER_RECORDING -> {
                File modelDir = new File(getSettings().recordingsDir, model.getSanitizedNamed());
                return new File(modelDir, formattedDate);
            }
            default -> {
                return new File(getSettings().recordingsDir);
            }
        }
    }

    private String getGroupOrModelName(Model model) {
        try {
            Optional<ModelGroup> modelGroup = getSettings().modelGroups.stream().filter(mg -> mg.getModelUrls().contains(model.getUrl())).findFirst();
            if (modelGroup.isPresent()) {
                return modelGroup.get().getName().replaceAll("[^a-zA-Z0-9.-]", "_");
            }
        } catch (Exception ex) {
            // Nothing
        }
        return model.getSanitizedNamed();
    }

    public String getServerUrl() {
        String scheme = getSettings().transportLayerSecurity ? "https" : "http";
        int port = getSettings().httpPort;
        String baseUrl = scheme + "://" + getSettings().httpServer + ":" + port + getContextPath();
        return baseUrl;
    }

    public String getContextPath() {
        String context = Optional.ofNullable(getSettings().servletContext).orElse("");
        if (!context.startsWith("/") && !context.isEmpty()) {
            context = '/' + context;
        }
        if (context.endsWith("/")) {
            context = context.substring(0, context.length() - 1);
        }
        return context;
    }

    public DateTimeFormatter getDateTimeFormatter() {
        var dtf = getSettings().dateTimeFormat;
        if (StringUtil.isNotBlank(dtf)) {
            return DateTimeFormatter.ofPattern(dtf);
        } else {
            return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT);
        }
    }

    //    public String getModelNotes(Model m) {
    //        return Config.getInstance().getSettings().modelNotes.getOrDefault(m.getUrl(), "");
    //    }

    public void disableSaving() {
        savingDisabled = true;
    }

    public void enableSaving() {
        savingDisabled = false;
    }

    public boolean isIgnored(Model model) {
        List<String> ignored = Config.getInstance().getSettings().ignoredModels;
        return ignored.contains(model.getUrl());
    }
}
