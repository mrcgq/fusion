package ctbrec.ui;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.Model;
import ctbrec.SubsequentAction;
import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.Site;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * Just a wrapper for Model, which augments it with JavaFX value binding properties, so that UI widgets get updated proeprly
 */
public class JavaFxModel implements Model {
    private final transient BooleanProperty onlineProperty = new SimpleBooleanProperty();
    private final transient BooleanProperty recordingProperty = new SimpleBooleanProperty();
    private final transient BooleanProperty pausedProperty = new SimpleBooleanProperty();
    private final transient BooleanProperty forcePriorityProperty = new SimpleBooleanProperty();
    private final transient SimpleIntegerProperty priorityProperty = new SimpleIntegerProperty();
    private final transient SimpleObjectProperty<Instant> lastSeenProperty = new SimpleObjectProperty<>();
    private final transient SimpleObjectProperty<Instant> lastRecordedProperty = new SimpleObjectProperty<>();
    private final transient SimpleObjectProperty<State> onlineStateProperty = new SimpleObjectProperty<>();

    private final Model delegate;

    public JavaFxModel(Model delegate) {
        this.delegate = delegate;
        setPriority(delegate.getPriority());
        setLastSeen(delegate.getLastSeen());
        setLastRecorded(delegate.getLastRecorded());
        try { // TODO: split online state updates and reading from cache, so that we don't need try-catch
            onlineStateProperty.setValue(delegate.getOnlineState(true));
        } catch (Exception e) {}
    }
    
    public void updateFrom(JavaFxModel other) {
        this.setSuspended(other.isSuspended());
        this.setForcePriority(other.isForcePriority());
        this.getOnlineProperty().set(other.getOnlineProperty().get());
        this.getRecordingProperty().set(other.getRecordingProperty().get());
        this.lastRecordedProperty().set(other.lastRecordedProperty().get());
        this.lastSeenProperty().set(other.lastSeenProperty().get());
        this.setRecordUntil(other.getRecordUntil());
        this.setRecordUntilSubsequentAction(other.getRecordUntilSubsequentAction());
        this.onlineStateProperty().set(other.onlineStateProperty().get());
    }

    @Override
    public String getUrl() {
        return delegate.getUrl();
    }

    @Override
    public void setUrl(String url) {
        delegate.setUrl(url);
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public void setName(String name) {
        delegate.setName(name);
    }

    @Override
    public String getSanitizedNamed() {
        return delegate.getSanitizedNamed();
    }

    @Override
    public String getPreview() {
        return delegate.getPreview();
    }

    @Override
    public void setPreview(String preview) {
        delegate.setPreview(preview);
    }

    @Override
    public List<String> getTags() {
        return delegate.getTags();
    }

    @Override
    public void setTags(List<String> tags) {
        delegate.setTags(tags);
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

    public BooleanProperty getOnlineProperty() {
        return onlineProperty;
    }

    public void setOnlineProperty(boolean online) {
        this.onlineProperty.set(online);
    }

    public BooleanProperty getRecordingProperty() {
        return recordingProperty;
    }

    public void setRecordingProperty(boolean recording) {
        this.recordingProperty.setValue(recording);
    }

    public BooleanProperty getPausedProperty() {
        return pausedProperty;
    }
    
    public BooleanProperty getForcePriorityProperty() {
    	return forcePriorityProperty;
    }

    public SimpleIntegerProperty getPriorityProperty() {
        return priorityProperty;
    }

    public Model getDelegate() {
        return delegate;
    }

    @Override
    public boolean isOnline() throws IOException, ExecutionException, InterruptedException {
        return delegate.isOnline();
    }

    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        return delegate.isOnline(ignoreCache);
    }
    
    public void setOnlineStateProperty(State state) throws IOException, ExecutionException {
        onlineStateProperty.set(state);
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        return delegate.getOnlineState(failFast);
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException {
        return delegate.getStreamSources();
    }

    @Override
    public void invalidateCacheEntries() {
        delegate.invalidateCacheEntries();
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
        SiteUiFactory.getUi(getSite()).login();
        delegate.receiveTip(tokens);
    }

    @Override
    public int[] getStreamResolution(boolean b) throws ExecutionException {
        return delegate.getStreamResolution(b);
    }

    @Override
    public boolean follow() throws IOException {
        SiteUiFactory.getUi(getSite()).login();
        return delegate.follow();
    }

    @Override
    public boolean unfollow() throws IOException {
        SiteUiFactory.getUi(getSite()).login();
        return delegate.unfollow();
    }

    @Override
    public void setSite(Site site) {
        delegate.setSite(site);
    }

    @Override
    public Site getSite() {
        return delegate.getSite();
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        delegate.readSiteSpecificData(data);
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        delegate.writeSiteSpecificData(data);
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public void setDescription(String description) {
        delegate.setDescription(description);
    }

    @Override
    public int getStreamUrlIndex() {
        return delegate.getStreamUrlIndex();
    }

    @Override
    public void setStreamUrlIndex(int streamUrlIndex) {
        delegate.setStreamUrlIndex(streamUrlIndex);
    }

    @Override
    public boolean isSuspended() {
        return delegate.isSuspended();
    }

    @Override
    public void setSuspended(boolean suspended) {
        delegate.setSuspended(suspended);
        pausedProperty.set(suspended);
    }

    @Override
    public void delay() {
        delegate.delay();
    }

    @Override
    public boolean isDelayed() {
        return delegate.isDelayed();
    }

    @Override
    public String getDisplayName() {
        return delegate.getDisplayName();
    }

    @Override
    public void setDisplayName(String name) {
        delegate.setDisplayName(name);
    }

    @Override
    public void setPriority(int priority) {
        delegate.setPriority(priority);
        priorityProperty.set(priority);
    }

    @Override
    public int getPriority() {
        return delegate.getPriority();
    }


    @Override
    public boolean isForcePriority() {
        return delegate.isForcePriority();
    }

    @Override
    public void setForcePriority(boolean forcePriority) {
        delegate.setForcePriority(forcePriority);
        forcePriorityProperty.set(forcePriority);
    }

    public SimpleObjectProperty<Instant> lastSeenProperty() {
        return lastSeenProperty;
    }

    @Override
    public void setLastSeen(Instant timestamp) {
        delegate.setLastSeen(timestamp);
        lastSeenProperty.set(timestamp);
    }

    @Override
    public Instant getLastSeen() {
        return delegate.getLastSeen();
    }

    public SimpleObjectProperty<Instant> lastRecordedProperty() {
        return lastRecordedProperty;
    }

    public SimpleObjectProperty<State> onlineStateProperty() {
        return onlineStateProperty;
    }

    @Override
    public void setLastRecorded(Instant timestamp) {
        delegate.setLastRecorded(timestamp);
        lastRecordedProperty.set(timestamp);
    }

    @Override
    public Instant getLastRecorded() {
        return delegate.getLastRecorded();
    }

    @Override
    public Instant getAddedTimestamp() {
        return delegate.getAddedTimestamp();
    }

    @Override
    public void setAddedTimestamp(Instant timestamp) {
        delegate.setAddedTimestamp(timestamp);
    }

    @Override
    public int compareTo(Model o) {
        return delegate.compareTo(o);
    }

    @Override
    public RecordingProcess createDownload() {
        return delegate.createDownload();
    }

    @Override
    public HttpHeaderFactory getHttpHeaderFactory() {
        return delegate.getHttpHeaderFactory();
    }

    @Override
    public Instant getRecordUntil() {
        return delegate.getRecordUntil();
    }

    @Override
    public void setRecordUntil(Instant instant) {
        delegate.setRecordUntil(instant);
    }

    @Override
    public SubsequentAction getRecordUntilSubsequentAction() {
        return delegate.getRecordUntilSubsequentAction();
    }

    @Override
    public void setRecordUntilSubsequentAction(SubsequentAction action) {
        delegate.setRecordUntilSubsequentAction(action);
    }

    @Override
    public boolean exists() throws IOException {
        return delegate.exists();
    }

    @Override
    public boolean isRecordingTimeLimited() {
        return delegate.isRecordingTimeLimited();
    }

    @Override
    public boolean isMarkedForLaterRecording() {
        return delegate.isMarkedForLaterRecording();
    }

    @Override
    public void setMarkedForLaterRecording(boolean marked) {
        delegate.setMarkedForLaterRecording(marked);
    }
}
