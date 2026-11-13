package ctbrec.recorder.download.hls;

import ctbrec.Model;
import ctbrec.io.HttpClient;
import ctbrec.io.IoUtils;
import ctbrec.recorder.download.hls.SegmentPlaylist.Segment;

import java.io.OutputStream;
import java.net.MalformedURLException;

public class MultiFileSegmentDownload extends SegmentDownload {
    public MultiFileSegmentDownload(Model model, SegmentPlaylist playlist, Segment segment, HttpClient client, OutputStream out) throws MalformedURLException {
        super(model, playlist, segment, client, out);
    }

    @Override
    public SegmentDownload call() {
        try {
            return super.call();
        } finally {
            IoUtils.close(getOutputStream(), "Couldn't close segment file " + getSegment().targetFile);
        }
    }
}
