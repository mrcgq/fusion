package ctbrec.sites.mfc;

import ctbrec.Config;
import ctbrec.recorder.download.StreamSource;
import ctbrec.recorder.download.dash.AdaptationSetType;
import ctbrec.recorder.download.dash.MPDtype;
import ctbrec.recorder.download.dash.PeriodType;
import ctbrec.recorder.download.dash.RepresentationType;
import ctbrec.sites.Site;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ctbrec.io.HttpConstants.*;

public class DashStreamSourceProvider implements StreamSourceProvider {

    private static final Logger LOG = LoggerFactory.getLogger(DashStreamSourceProvider.class);

    private final Config config;

    private final Site site;

    public DashStreamSourceProvider(Config config, Site site) {
        this.config = config;
        this.site = site;
    }

    @Override
    public List<StreamSource> getStreamSources(String streamUrl) throws JAXBException, IOException {
        String manifest = getManifest(streamUrl);
        JAXBContext jc = JAXBContext.newInstance(MPDtype.class.getPackage().getName());
        Unmarshaller u = jc.createUnmarshaller();
        @SuppressWarnings("unchecked")
        JAXBElement<MPDtype> root = (JAXBElement<MPDtype>) u.unmarshal(new ByteArrayInputStream(manifest.getBytes()));
        MPDtype mpd = root.getValue();
        List<PeriodType> periods = mpd.getPeriod();
        if (periods.isEmpty()) {
            return Collections.emptyList();
        } else {
            PeriodType period = periods.get(0);
            List<AdaptationSetType> videoStreams = new ArrayList<>();
            List<AdaptationSetType> adaptationSets = period.getAdaptationSet();
            for (AdaptationSetType adaptationSet : adaptationSets) {
                String mimeType = adaptationSet.getMimeType();
                if (mimeType.equalsIgnoreCase("video/mp4")) {
                    videoStreams.add(adaptationSet);
                }
            }

            return videoStreams.stream().map(ast -> {
                RepresentationType representation = ast.getRepresentation().get(0);
                StreamSource src = new StreamSource();
                src.setWidth(ast.getWidth().intValue());
                src.setHeight(ast.getHeight().intValue());
                src.setBandwidth((int) representation.getBandwidth());
                src.setMediaPlaylistUrl(streamUrl);
                return src;
            }).toList();
        }
    }

    private String getManifest(String url) throws IOException {
        // @formatter:off
        Request request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, "en-US,en;q=0.5")
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .header(ORIGIN, site.getBaseUrl())
                .header(REFERER, site.getBaseUrl())
                .header(CONNECTION, KEEP_ALIVE)
                .build(); // @formatter:on
        LOG.trace("Loading manifest {}", url);
        try (Response response = site.getHttpClient().execute(request)) {
            return response.body().string();
        }
    }
}
