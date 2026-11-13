package ctbrec.recorder.download;

import java.util.Map;

public class HttpHeaderFactoryImpl implements HttpHeaderFactory {

    private Map<String, String> masterPlaylistHeaders;
    private Map<String, String> segmentPlaylistHeaders;
    private Map<String, String> segmentHeaders;

    @Override
    public Map<String, String> createMasterPlaylistHeaders() {
        return masterPlaylistHeaders;
    }

    @Override
    public Map<String, String> createSegmentPlaylistHeaders() {
        return segmentPlaylistHeaders;
    }

    @Override
    public Map<String, String> createSegmentHeaders() {
        return segmentHeaders;
    }

    public void setMasterPlaylistHeaders(Map<String, String> masterPlaylistHeaders) {
        this.masterPlaylistHeaders = masterPlaylistHeaders;
    }

    public void setSegmentPlaylistHeaders(Map<String, String> segmentPlaylistHeaders) {
        this.segmentPlaylistHeaders = segmentPlaylistHeaders;
    }

    public void setSegmentHeaders(Map<String, String> segmentHeaders) {
        this.segmentHeaders = segmentHeaders;
    }
}
