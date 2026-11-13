package ctbrec.sites.fc2live;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.io.HttpClient;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;

public class Fc2MergedHlsDownload extends MergedFfmpegHlsDownload {

    private static final Logger LOG = LoggerFactory.getLogger(Fc2MergedHlsDownload.class);

    public Fc2MergedHlsDownload(HttpClient client) {
        super(client);
    }

    @Override
    public Fc2MergedHlsDownload call() throws Exception {
        Fc2Model fc2Model = (Fc2Model) model;
        try {
            fc2Model.openWebsocket();
            super.call();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Couldn't start download for {}", model, e);
        } finally {
            fc2Model.closeWebsocket();
        }
        return this;
    }
}
