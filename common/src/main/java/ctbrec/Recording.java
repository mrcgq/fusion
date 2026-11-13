package ctbrec;

import ctbrec.event.EventBusHolder;
import ctbrec.event.RecordingStateChangedEvent;
import ctbrec.io.IoUtils;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.VideoLengthDetector;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Future;

import static ctbrec.Recording.State.*;

@Slf4j
@Getter
@Setter
@NoArgsConstructor
public class Recording implements Serializable {

    private String id;
    private Model model;
    private transient RecordingProcess recordingProcess;
    private transient Future<RecordingProcess> currentIteration;
    private Instant startDate;
    private String path;
    private State status = State.UNKNOWN;
    private int progress = -1;
    private long sizeInByte = -1;
    private String metaDataFile;
    private boolean singleFile = false;
    private boolean pinned = false;
    private String note;
    private Set<String> associatedFiles = new HashSet<>();
    private File absoluteFile = null;
    private File postProcessedFile = null;
    private int selectedResolution = -1;
    private long lastSizeUpdate = 0;
    private String recordingsDir;

    public Recording(String recordingsDir) {
        this.recordingsDir = recordingsDir;
    }

    public enum State {
        RECORDING("recording"),
        GENERATING_PLAYLIST("generating playlist"),
        POST_PROCESSING("post-processing"),
        FINISHED("finished"),
        DOWNLOADING("downloading"),
        DELETING("deleting"),
        DELETED("deleted"),
        UNKNOWN("unknown"),
        WAITING("waiting"),
        FAILED("failed");

        private final String desc;

        State(String desc) {
            this.desc = desc;
        }

        @Override
        public String toString() {
            return desc;
        }
    }

    public void setStatusWithEvent(State status) {
        setStatus(status);
        fireStatusEvent(status);
    }

    public void setPath(String path) {
        this.path = path;
    }

    public File getAbsoluteFile() {
        if (absoluteFile == null) {
            File recordingsFile = new File(recordingsDir, path);
            absoluteFile = recordingsFile;
        }
        return absoluteFile;
    }

    public void setAbsoluteFile(File absoluteFile) {
        this.absoluteFile = absoluteFile;
    }

    public File getPostProcessedFile() {
        if (postProcessedFile == null) {
            setPostProcessedFile(getAbsoluteFile());
        }
        return postProcessedFile;
    }

    public void setPostProcessedFile(File postProcessedFile) {
        this.postProcessedFile = postProcessedFile;
    }

    public long getSizeInByte() {
        if (sizeInByte == -1 || getStatus() == RECORDING) {
            refresh();
        }
        return sizeInByte;
    }

    public void setSizeInByte(long sizeInByte) {
        this.sizeInByte = sizeInByte;
    }

    public void postprocess() {
        getRecordingProcess().postProcess(this);
    }

    private void fireStatusEvent(State status) {
        RecordingStateChangedEvent evt = new RecordingStateChangedEvent(getRecordingProcess().getTarget(), status, getModel(), getStartDate());
        EventBusHolder.BUS.post(evt);
    }


    public int getSelectedResolution() {
        if ((selectedResolution == -1 || selectedResolution == StreamSource.UNKNOWN) && recordingProcess != null) {
            selectedResolution = recordingProcess.getSelectedResolution();
        }
        return selectedResolution;
    }

    public void setSelectedResolution(int selectedResolution) {
        this.selectedResolution = selectedResolution;
    }

    public Duration getLength() {
        File ppFile = getPostProcessedFile();
        if (ppFile.isDirectory()) {
            File playlist = new File(ppFile, "playlist.m3u8");
            return VideoLengthDetector.getLength(playlist);
        } else {
            return VideoLengthDetector.getLength(ppFile);
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Recording other))
            return false;
        if (getId() == null) {
            return other.getId() == null;
        } else return getId().equals(other.getId());
    }

    @Override
    public String toString() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(Config.RECORDING_DATE_FORMAT);
        LocalDateTime localStartDate = LocalDateTime.ofInstant(getStartDate(), ZoneId.systemDefault());
        return getModel().getSanitizedNamed() + '_' + formatter.format(localStartDate);
    }

    private long getSize() {
        try {
            Set<File> files = getAllRecordingFiles();
            long sum = 0;
            for (File file : files) {
                if (file.isDirectory()) {
                    sum += IoUtils.getDirectorySize(file);
                } else {
                    if (!file.exists()) {
                        if (file.getName().endsWith(".m3u8")) {
                            sum += IoUtils.getDirectorySize(file.getParentFile());
                        }
                    } else {
                        sum += file.length();
                    }
                }
            }
            return sum;
        } catch (IOException e) {
            log.error("Couldn't determine recording size", e);
            return -1;
        }
    }

    public Set<File> getAllRecordingFiles() throws IOException {
        Set<File> files = new HashSet<>();
        if (absoluteFile != null) {
            files.add(absoluteFile.getCanonicalFile());
        }
        if (postProcessedFile != null) {
            files.add(postProcessedFile.getCanonicalFile());
        }
        for (String associatedFile : associatedFiles) {
            files.add(new File(associatedFile).getCanonicalFile());
        }
        return files;
    }

    public void refresh() {
        if (getStatus() == RECORDING && recordingProcess != null) {
            sizeInByte = recordingProcess.getSizeInByte();
        } else {
            long now = System.currentTimeMillis();
            if (now - lastSizeUpdate > 2500) {
                log.trace("full size update for {}", this);
                sizeInByte = getSize();
                lastSizeUpdate = now;
            }
        }
    }

    public boolean canBePostProcessed() {
        return getStatus() == FAILED || getStatus() == WAITING || getStatus() == FINISHED;
    }

    public Optional<File> getContactSheet() {
        return getAssociatedFiles().stream()
                .filter(filePath -> filePath.endsWith(".jpg"))
                .findFirst()
                .map(File::new);
    }
}
