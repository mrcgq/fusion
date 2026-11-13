package ctbrec.sites.manyvids;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ctbrec.io.HttpClient;
import ctbrec.recorder.download.hls.MergedFfmpegHlsDownload;

public class MVLiveMergedHlsDownload extends MergedFfmpegHlsDownload {

    private static final Logger LOG = LoggerFactory.getLogger(MVLiveMergedHlsDownload.class);

    private transient ScheduledExecutorService scheduler;

    public MVLiveMergedHlsDownload(HttpClient client) {
        super(client);
    }

    @Override
    public MVLiveMergedHlsDownload call() throws Exception {
        try {
            scheduler = new ScheduledThreadPoolExecutor(1, r -> {
                Thread t = new Thread(r);
                t.setDaemon(true);
                t.setName("MVLive CF cookie updater");
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
            scheduler.scheduleAtFixedRate(this::updateCloudFlareCookies, 2, 2, TimeUnit.MINUTES);
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
