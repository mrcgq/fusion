package ctbrec.recorder.download;

import java.util.Map;

public interface HttpHeaderFactory {

    Map<String, String> createMasterPlaylistHeaders();
    Map<String, String> createSegmentPlaylistHeaders();
    Map<String, String> createSegmentHeaders();
}
