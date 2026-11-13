package ctbrec;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;
import ctbrec.recorder.download.HttpHeaderFactory;
import ctbrec.recorder.download.RecordingProcess;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.Site;

import javax.xml.bind.JAXBException;
import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public interface Model extends Comparable<Model>, Serializable {

    long RECORD_INDEFINITELY = 9000000000000000000L;
    int MAX_PRIO = 10_000;

    enum State {
        ONLINE("online"),
        OFFLINE("offline"),
        AWAY("away"),
        PRIVATE("private"),
        GROUP("group"),
        UNKNOWN("unknown"),
        UNCHECKED("unchecked");

        final String display;

        State(String display) {
            this.display = display;
        }

        @Override
        public String toString() {
            return display;
        }
    }

    String getUrl();

    void setUrl(String url);

    String getDisplayName();

    void setDisplayName(String name);

    String getName();

    void setName(String name);

    /**
     * Returns a name, which is safe to use with the filesystem
     */
    String getSanitizedNamed();

    String getPreview();

    void setPreview(String preview);

    List<String> getTags();

    void setTags(List<String> tags);

    String getDescription();

    void setDescription(String description);

    int getStreamUrlIndex();

    void setStreamUrlIndex(int streamUrlIndex);

    boolean isOnline() throws IOException, ExecutionException, InterruptedException;

    boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException;

    State getOnlineState(boolean failFast) throws IOException, ExecutionException;

    List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException;

    void invalidateCacheEntries();

    void receiveTip(Double tokens) throws IOException;

    void setLastSeen(Instant timestamp);

    Instant getLastSeen();

    void setLastRecorded(Instant timestamp);

    Instant getLastRecorded();

    void setAddedTimestamp(Instant timestamp);

    Instant getAddedTimestamp();

    /**
     * Determines the stream resolution for this model
     *
     * @param failFast If set to true, the method returns immediately, even if the resolution is unknown. If
     *                 the resolution is unknown, the array contains 0,0
     * @return a tupel of width and height represented by an int[2]
     * @throws ExecutionException
     */
    int[] getStreamResolution(boolean failFast) throws ExecutionException;

    boolean follow() throws IOException;

    boolean unfollow() throws IOException;

    void setSite(Site site);

    Site getSite();

    void writeSiteSpecificData(Map<String, String> data);

    void readSiteSpecificData(Map<String, String> data);

    boolean isSuspended();

    void setSuspended(boolean suspended);

    boolean isForcePriority();

    void setForcePriority(boolean forcePriority);

    void delay();

    boolean isDelayed();

    boolean isMarkedForLaterRecording();

    void setMarkedForLaterRecording(boolean marked);

    RecordingProcess createDownload();

    void setPriority(int priority);

    int getPriority();

    HttpHeaderFactory getHttpHeaderFactory();

    boolean isRecordingTimeLimited();

    Instant getRecordUntil();

    void setRecordUntil(Instant instant);

    SubsequentAction getRecordUntilSubsequentAction();

    void setRecordUntilSubsequentAction(SubsequentAction action);

    /**
     * Check, if this model account exists
     *
     * @return true, if it exists, false otherwise
     * @throws IOException
     */
    boolean exists() throws IOException;

}
