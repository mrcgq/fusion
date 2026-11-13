package ctbrec.sites.fc2live;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.recorder.download.hls.HlsdlDownload;

public class Fc2HlsdlDownload extends HlsdlDownload {

    private static final Logger LOG = LoggerFactory.getLogger(Fc2HlsdlDownload.class);


    @Override
    public void init(Config config, Model model, Instant startTime, ExecutorService executorService) throws IOException {
        super.init(config, model, startTime, executorService);
        Fc2Model fc2Model = (Fc2Model) model;
        try {
            fc2Model.openWebsocket();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOG.error("Couldn't start download for {}", model, e);
        }
    }

    @Override
    public void finalizeDownload() {
        Fc2Model fc2Model = (Fc2Model) model;
        fc2Model.closeWebsocket();
        super.finalizeDownload();
    }
}
