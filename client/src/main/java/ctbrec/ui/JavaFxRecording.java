package ctbrec.ui;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.Recording;
import ctbrec.recorder.download.RecordingProcess;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public class JavaFxRecording extends Recording {

    private final transient StringProperty statusProperty = new SimpleStringProperty();
    private final transient StringProperty progressProperty = new SimpleStringProperty();
    private final transient StringProperty notesProperty = new SimpleStringProperty();
    private final transient LongProperty sizeProperty = new SimpleLongProperty();
    private final Recording delegate;
    private long lastValue = 0;

    public JavaFxRecording(Recording recording) {
        this.delegate = recording;
        setStatus(recording.getStatus());
        setSizeInByte(recording.getSizeInByte());
        setProgress(recording.getProgress());
        setNote(recording.getNote());
        notesProperty.addListener((obs, oldV, newV) -> delegate.setNote(newV));
    }

    public Recording getDelegate() {
        return delegate;
    }

    @Override
    public Model getModel() {
        return delegate.getModel();
    }

    @Override
    public void setModel(Model model) {
        delegate.setModel(model);
    }

    @Override
    public Instant getStartDate() {
        return delegate.getStartDate();
    }

    @Override
    public void setStartDate(Instant startDate) {
        delegate.setStartDate(startDate);
    }

    @Override
    public State getStatus() {
        return delegate.getStatus();
    }

    public StringProperty getStatusProperty() {
        return statusProperty;
    }

    @Override
    public void setStatus(State status) {
        delegate.setStatus(status);
        switch (status) {
            case RECORDING:
                statusProperty.set("recording");
                break;
            case GENERATING_PLAYLIST:
                statusProperty.set("generating playlist");
                break;
            case FINISHED:
                statusProperty.set("finished");
                break;
            case DOWNLOADING:
                statusProperty.set("downloading");
                break;
            case POST_PROCESSING:
                statusProperty.set("post-processing");
                break;
            case DELETED:
                statusProperty.set("deleted");
                break;
            case DELETING:
                statusProperty.set("deleting");
                break;
            case FAILED:
                statusProperty.set("failed");
                break;
            case WAITING:
                statusProperty.set("waiting");
                break;
            case UNKNOWN:
            default:
                statusProperty.set("unknown");
                break;
        }
        if (isPinned()) {
            statusProperty.set(statusProperty.get() + " 🔒");
        }
    }

    @Override
    public int getProgress() {
        return delegate.getProgress();
    }

    @Override
    public void setProgress(int progress) {
        delegate.setProgress(progress);
        if (progress >= 0) {
            progressProperty.set(progress + "%");
        } else {
            progressProperty.set("");
        }
    }

    @Override
    public void setSizeInByte(long sizeInByte) {
        delegate.setSizeInByte(sizeInByte);
        sizeProperty.set(sizeInByte);
    }

    public StringProperty getProgressProperty() {
        return progressProperty;
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return delegate.equals(obj);
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    public void update(Recording updated) {
        if (!Config.getInstance().getSettings().localRecording) {
            if (getStatus() == State.DOWNLOADING && updated.getStatus() != State.DOWNLOADING) {
                // ignore, because the the status coming from the server is FINISHED and we are
                // overriding it with DOWNLOADING
                return;
            }
        }
        setStatus(updated.getStatus());
        setProgress(updated.getProgress());
        setSizeInByte(updated.getSizeInByte());
        setSingleFile(updated.isSingleFile());
        setAssociatedFiles(updated.getAssociatedFiles());
    }

    @Override
    public void setPath(String path) {
        delegate.setPath(path);
    }

    @Override
    public long getSizeInByte() {
        return delegate.getSizeInByte();
    }

    public LongProperty getSizeProperty() {
        return sizeProperty;
    }

    @Override
    public void setMetaDataFile(String metaDataFile) {
        delegate.setMetaDataFile(metaDataFile);
    }

    @Override
    public String getMetaDataFile() {
        return delegate.getMetaDataFile();
    }

    @Override
    public boolean isSingleFile() {
        return delegate.isSingleFile();
    }

    @Override
    public void setSingleFile(boolean singleFile) {
        delegate.setSingleFile(singleFile);
    }

    @Override
    public boolean isPinned() {
        return delegate.isPinned();
    }

    @Override
    public void setPinned(boolean pinned) {
        delegate.setPinned(pinned);
        setStatus(getStatus());
    }

    public boolean valueChanged() {
        boolean changed = getSizeInByte() != lastValue;
        lastValue = getSizeInByte();
        return changed;
    }

    @Override
    public String getNote() {
        return delegate.getNote();
    }

    @Override
    public void setNote(String note) {
        delegate.setNote(note);
        notesProperty.set(note);
    }

    public StringProperty getNoteProperty() {
        return notesProperty;
    }

    @Override
    public File getAbsoluteFile() {
        return delegate.getAbsoluteFile();
    }

    @Override
    public void setAbsoluteFile(File absoluteFile) {
        delegate.setAbsoluteFile(absoluteFile);
    }

    @Override
    public String getId() {
        return delegate.getId();
    }

    @Override
    public void setId(String id) {
        delegate.setId(id);
    }

    @Override
    public void setStatusWithEvent(State status) {
        delegate.setStatusWithEvent(status);
    }

    @Override
    public File getPostProcessedFile() {
        return delegate.getPostProcessedFile();
    }

    @Override
    public void setPostProcessedFile(File postProcessedFile) {
        delegate.setPostProcessedFile(postProcessedFile);
    }

    @Override
    public RecordingProcess getRecordingProcess() {
        return delegate.getRecordingProcess();
    }

    @Override
    public void setRecordingProcess(RecordingProcess recordingProcess) {
        delegate.setRecordingProcess(recordingProcess);
    }

    @Override
    public Duration getLength() {
        return delegate.getLength();
    }

    @Override
    public void refresh() {
        delegate.refresh();
    }

    @Override
    public boolean canBePostProcessed() {
        return delegate.canBePostProcessed();
    }

    @Override
    public Set<String> getAssociatedFiles() {
        return delegate.getAssociatedFiles();
    }

    @Override
    public void setAssociatedFiles(Set<String> associatedFiles) {
        delegate.setAssociatedFiles(associatedFiles);
    }

    @Override
    public Optional<File> getContactSheet() {
        return delegate.getContactSheet();
    }

    @Override
    public int getSelectedResolution() {
        return delegate.getSelectedResolution();
    }
}
