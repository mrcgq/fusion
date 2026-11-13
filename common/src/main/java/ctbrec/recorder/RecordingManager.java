package ctbrec.recorder;

import com.fasterxml.jackson.databind.ObjectMapper;
import ctbrec.Config;
import ctbrec.MigrateModel5_1_2;
import ctbrec.Recording;
import ctbrec.Recording.State;
import ctbrec.io.json.ObjectMapperFactory;
import ctbrec.io.json.dto.RecordingDto;
import ctbrec.io.json.mapper.RecordingMapper;
import ctbrec.sites.Site;
import ctbrec.sites.SiteUtil;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.mapstruct.factory.Mappers;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

import static ctbrec.Recording.State.*;
import static ctbrec.io.IoUtils.deleteDirectory;
import static ctbrec.io.IoUtils.deleteEmptyParents;
import static java.nio.file.StandardOpenOption.*;

@Slf4j
public class RecordingManager {

    private static final ObjectMapper mapper = ObjectMapperFactory.getMapper();
    private final Config config;
    private final List<Site> sites;
    private final List<Recording> recordings = new ArrayList<>();
    private final ReentrantLock recordingsLock = new ReentrantLock();

    public RecordingManager(Config config, List<Site> sites) throws IOException {
        this.config = config;
        this.sites = sites;

        loadRecordings();
    }

    public void add(Recording rec) throws IOException {
        File recordingsMetaDir = getDir();
        String filename = rec.toString() + ".json";
        File recordingMetaData = new File(recordingsMetaDir, filename);
        rec.setMetaDataFile(recordingMetaData.getCanonicalPath());
        saveRecording(rec);
        recordingsLock.lock();
        try {
            recordings.add(rec);
        } finally {
            recordingsLock.unlock();
        }
    }

    public void saveRecording(Recording rec) throws IOException {
        if (rec.getMetaDataFile() != null) {
            File recordingMetaData = new File(rec.getMetaDataFile());
            String json = mapper.writeValueAsString(Mappers.getMapper(RecordingMapper.class).toDto(rec));
            rec.setMetaDataFile(recordingMetaData.getAbsolutePath());
            Files.createDirectories(recordingMetaData.getParentFile().toPath());
            Files.writeString(recordingMetaData.toPath(), json, CREATE, WRITE, TRUNCATE_EXISTING);
        }
    }

    private void loadRecordings() throws IOException {
        File recordingsMetaDir = getDir();
        File[] metaFiles = recordingsMetaDir.listFiles((file, name) -> name.endsWith(".json"));
        if (metaFiles != null) {
            for (File file : metaFiles) {
                String json = Files.readString(file.toPath());
                json = migrateJson(json);
                try {
                    Recording recording = Mappers.getMapper(RecordingMapper.class).toRecording(mapper.readValue(json, RecordingDto.class));
                    recording.setMetaDataFile(file.getCanonicalPath());
                    SiteUtil.getSiteForModel(sites, recording.getModel()).ifPresent(s -> recording.getModel().setSite(s));
                    recording.setMetaDataFile(file.getCanonicalPath());
                    saveRecording(recording);
                    loadRecording(recording);
                } catch (Exception e) {
                    log.error("Couldn't load recording {}", file, e);
                }
            }
        }
    }

    private String migrateJson(String json) {
        JSONObject r = new JSONObject(json);
        JSONObject model = r.getJSONObject("model");
        MigrateModel5_1_2.migrate(model);
        return r.toString();
    }

    private void loadRecording(Recording recording) throws IOException {
        if (recording.getStatus() == RECORDING || recording.getStatus() == GENERATING_PLAYLIST || recording.getStatus() == POST_PROCESSING) {
            recording.setStatus(WAITING);
        }
        if (recording.getId() == null) {
            recording.setId(UUID.randomUUID().toString());
            saveRecording(recording);
        }
        if (recordingExists(recording)) {
            recordings.add(recording);
        } else {
            if (config.getSettings().deleteOrphanedRecordingMetadata) {
                log.info("Recording {} does not exist anymore -> deleting recording", recording);
                boolean deleted = Files.deleteIfExists(new File(recording.getMetaDataFile()).toPath());
                log.info("Recording {} has {}been deleted", recording, deleted ? "" : "not ");
            } else {
                log.info("Recording {} does not exist anymore -> ignoring recording", recording);
            }
        }
    }

    private boolean recordingExists(Recording recording) {
        return recording.getAbsoluteFile().exists();
    }

    private File getDir() {
        File configDir = config.getConfigDir();
        File recordingsMetaDir = new File(configDir, "recordings");
        return recordingsMetaDir;
    }

    /**
     * Deletes a recording from disk and removes it from ctbrec
     *
     * @param recording Recording to delete
     * @throws IOException if the deletion of the recording or of any empty parent directories fails
     */
    public void delete(Recording recording) throws IOException {
        if (recording.isPinned()) {
            throw new RecordingPinnedException(recording);
        }

        recordingsLock.lock();
        try {
            int idx = recordings.indexOf(recording);
            recording = recordings.get(idx);

            recording.setStatus(State.DELETING);
            File path = recording.getAbsoluteFile();
            boolean isFile = path.isFile();
            log.debug("Deleting {}", path);

            // delete the video files
            if (isFile) {
                Files.delete(path.toPath());
            } else {
                deleteDirectory(path);
            }

            // delete files associated with this recording
            for (String associated : recording.getAssociatedFiles()) {
                File f = new File(associated);
                if (f.isFile()) {
                    Files.delete(f.toPath());
                    deleteEmptyParents(f.getParentFile());
                } else {
                    deleteDirectory(f);
                    deleteEmptyParents(f);
                }
            }

            // delete the meta data

            Files.deleteIfExists(new File(recording.getMetaDataFile()).toPath());

            // delete empty parent files
            if (isFile) {
                deleteEmptyParents(path.getParentFile());
            } else {
                deleteEmptyParents(path);
            }

            // remove from data structure
            recordings.remove(recording);
            recording.setStatus(State.DELETED);
        } finally {
            recordingsLock.unlock();
        }
    }

    /**
     * Removes the recording from ctbrec, but does not touch the actual file
     *
     * @param recording Recording to delete
     * @throws IOException if the deletion of the metadata or of any empty parent directories fails
     */
    public void remove(Recording recording) throws IOException {
        recordingsLock.lock();
        try {
            int idx = recordings.indexOf(recording);
            recording = recordings.get(idx);
            // delete the meta data
            Files.deleteIfExists(new File(recording.getMetaDataFile()).toPath());
            // remove from data structure
            recordings.remove(recording);
        } finally {
            recordingsLock.unlock();
        }
    }

    public List<Recording> getAll() {
        recordingsLock.lock();
        try {
            return new ArrayList<>(recordings);
        } finally {
            recordingsLock.unlock();
        }
    }

    public void pin(Recording recording) throws IOException {
        recordingsLock.lock();
        try {
            Recording local = getRecording(recording);
            local.setPinned(true);
            saveRecording(local);
        } finally {
            recordingsLock.unlock();
        }
    }

    public void unpin(Recording recording) throws IOException {
        recordingsLock.lock();
        try {
            Recording local = getRecording(recording);
            local.setPinned(false);
            saveRecording(local);
        } finally {
            recordingsLock.unlock();
        }
    }

    public void setNote(Recording rec, String note) throws IOException {
        recordingsLock.lock();
        try {
            Recording local = getRecording(rec);
            local.setNote(note);
            saveRecording(local);
        } finally {
            recordingsLock.unlock();
        }
    }

    private Recording getRecording(Recording rec) {
        for (Recording r : getAll()) {
            if (Objects.equals(r, rec)) {
                return r;
            }
        }
        throw new NoSuchElementException("Recording not found");
    }
}
