package ctbrec.sites.manyvids;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.io.HttpClient;
import ctbrec.recorder.download.hls.HlsDownload;

public class MVLiveHlsDownload extends HlsDownload {
    private static final Logger LOG = LoggerFactory.getLogger(MVLiveHlsDownload.class);

    private transient ScheduledExecutorService scheduler;

    public MVLiveHlsDownload(HttpClient client) {
        super(client);
    }

    @Override
    public MVLiveHlsDownload call() throws Exception {
        try {
            scheduler = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("MVLive CF cookie updater");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::updateCloudFlareCookies, 120, 120, TimeUnit.SECONDS);
            updateCloudFlareCookies();
            super.call();
        } finally {
            scheduler.shutdown();
        }
        return this;
    }

    private void updateCloudFlareCookies() {
        try {
            ((MVLiveModel)getModel()).updateCloudFlareCookies();
        } catch (IOException e) {
            LOG.error("Couldn't update cloudflare cookies for model {}", getModel(), e);
        }
    }
}
