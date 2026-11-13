package ctbrec;


import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.HttpHeaderFactoryImpl;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.hls.HlsDownload;
import ctbrec.recorder.download.hls.HlsdlDownload;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;
import ctbrec.sites.Site;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

import static ctbrec.io.HttpConstants.USER_AGENT;

public abstract class AbstractModel implements Model {

    private String url;
    private String name;
    private String sanitizedName;
    private String displayName;
    private String preview;
    private String description;
    private List<String> tags = new ArrayList<>();
    private int streamUrlIndex = -1;
    private int priority = new Settings().defaultPriority;
    private boolean suspended = false;
    private boolean forcePriority = false;
    private boolean markedForLaterRecording = false;
    protected transient Site site;
    protected State onlineState = State.UNCHECKED;
    private Instant lastSeen;
    private Instant lastRecorded;
    private Instant recordUntil;
    private Instant addedTimestamp = Instant.EPOCH;
    private transient Instant delayUntil = Instant.EPOCH;
    private SubsequentAction recordUntilSubsequentAction;

    @Override
    public boolean isOnline() throws IOException, ExecutionException, InterruptedException {
        return isOnline(false);
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public void setUrl(String url) {
        this.url = url;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void setName(String name) {
        this.name = name;
        this.sanitizedName = Optional.ofNullable(getName()).orElse("").replaceAll("[^a-zA-Z0-9.-]", "_");
    }

    @Override
    public String getDisplayName() {
        if (displayName != null) {
            return displayName;
        } else {
            return getName();
        }
    }

    @Override
    public void setDisplayName(String name) {
        this.displayName = name;
    }

    @Override
    public String getSanitizedNamed() {
        return sanitizedName;
    }

    @Override
    public String getPreview() {
        return preview;
    }

    @Override
    public void setPreview(String preview) {
        this.preview = preview;
    }

    @Override
    public List<String> getTags() {
        return tags;
    }

    @Override
    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int getStreamUrlIndex() {
        return streamUrlIndex;
    }

    @Override
    public void setStreamUrlIndex(int streamUrlIndex) {
        this.streamUrlIndex = streamUrlIndex;
    }

    @Override
    public void readSiteSpecificData(Map<String, String> data) {
        // noop default implementation, can be overriden by concrete models
    }

    @Override
    public void writeSiteSpecificData(Map<String, String> data) {
        // noop default implementation, can be overriden by concrete models
    }

    @Override
    public boolean isSuspended() {
        return suspended;
    }

    @Override
    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    @Override
    public boolean isForcePriority() { return forcePriority; }

    @Override
    public void setForcePriority(boolean forcePriority) { this.forcePriority = forcePriority; }

    @Override
    public void delay() {
        this.delayUntil = Instant.now().plusSeconds(120);
    }

    @Override
    public boolean isDelayed() {
        return this.delayUntil.isAfter(Instant.now());
    }

    @Override
    public boolean isMarkedForLaterRecording() {
        return markedForLaterRecording;
    }

    @Override
    public void setMarkedForLaterRecording(boolean marked) {
        this.markedForLaterRecording = marked;
    }

    @Override
    public State getOnlineState(boolean failFast) throws IOException, ExecutionException {
        return onlineState;
    }

    public void setOnlineState(State status) {
        this.onlineState = status;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getUrl() == null) ? 0 : getUrl().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (!(obj instanceof Model))
            return false;
        Model other = (Model) obj;
        if (getName() == null) {
            if (other.getName() != null)
                return false;
        } else if (!getSanitizedNamed().equals(other.getSanitizedNamed()))
            return false;
        if (getUrl() == null) {
            if (other.getUrl() != null)
                return false;
        } else if (!getUrl().equals(other.getUrl()))
            return false;
        return true;
    }

    @Override
    public int compareTo(Model o) {
        String thisName = Optional.ofNullable(getDisplayName()).orElse("").toLowerCase();
        String otherName = Optional.ofNullable(o).map(Model::getDisplayName).orElse("").toLowerCase();
        return thisName.compareTo(otherName);
    }

    @Override
    public String toString() {
        return getName();
    }

    @Override
    public void setSite(Site site) {
        this.site = site;
    }

    @Override
    public Site getSite() {
        return site;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    public void setPriority(int priority) {
        this.priority = priority;
    }

    @Override
    public Instant getLastSeen() {
        return Optional.ofNullable(lastSeen).orElse(Instant.EPOCH);
    }

    @Override
    public void setLastSeen(Instant lastSeen) {
        this.lastSeen = lastSeen;
    }

    @Override
    public Instant getLastRecorded() {
        return Optional.ofNullable(lastRecorded).orElse(Instant.EPOCH);
    }

    @Override
    public void setLastRecorded(Instant lastRecorded) {
        this.lastRecorded = lastRecorded;
    }

    @Override
    public Instant getAddedTimestamp() {
        return addedTimestamp;
    }

    @Override
    public void setAddedTimestamp(Instant timestamp) {
        this.addedTimestamp = timestamp;
    }

    @Override
    public boolean isRecordingTimeLimited() {
        return !getRecordUntil().equals(Instant.ofEpochMilli(RECORD_INDEFINITELY));
    }

    @Override
    public Instant getRecordUntil() {
        return Optional.ofNullable(recordUntil).orElse(Instant.ofEpochMilli(RECORD_INDEFINITELY));
    }

    @Override
    public void setRecordUntil(Instant recordUntil) {
        this.recordUntil = recordUntil;
    }

    @Override
    public SubsequentAction getRecordUntilSubsequentAction() {
        return Optional.ofNullable(recordUntilSubsequentAction).orElse(SubsequentAction.PAUSE);
    }

    @Override
    public void setRecordUntilSubsequentAction(SubsequentAction recordUntilSubsequentAction) {
        this.recordUntilSubsequentAction = recordUntilSubsequentAction;
    }

    @Override
    public RecordingProcess createDownload() {
        if (Config.getInstance().getSettings().useHlsdl) {
            return new HlsdlDownload();
        } else {
            if (Config.isServerMode() && !Config.getInstance().getSettings().recordSingleFile) {
                return new HlsDownload(getSite().getHttpClient());
            } else {
                return new MergedFfmpegHlsDownload(getSite().getHttpClient());
            }
        }
    }

    @Override
    public HttpHeaderFactory getHttpHeaderFactory() {
        HttpHeaderFactoryImpl fac = new HttpHeaderFactoryImpl();
        fac.setMasterPlaylistHeaders(new HashMap<>());
        fac.setSegmentPlaylistHeaders(new HashMap<>());
        fac.setSegmentHeaders(new HashMap<>());
        return fac;
    }

    @Override
    public boolean exists() throws IOException {
        Request req = new Request.Builder() // @formatter:off
                .url(getUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build(); // @formatter:on
        try (Response response = getSite().getHttpClient().execute(req)) {
            if (!response.isSuccessful() && response.code() == 404) {
                return false;
            }
        }
        return true;
    }
}
