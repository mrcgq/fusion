package ctbrec.sites.mfc;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.xml.bind.JAXBException;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;

import ctbrec.recorder.download.StreamSource;

public interface StreamSourceProvider {

    public List<StreamSource> getStreamSources(String streamUrl) throws IOException, ExecutionException, ParseException, PlaylistException, JAXBException;

}
