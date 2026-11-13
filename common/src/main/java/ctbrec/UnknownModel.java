package ctbrec;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import com.iheartradio.m3u8.ParseException;
import com.iheartradio.m3u8.PlaylistException;

import ctbrec.io.HttpClient;
import ctbrec.recorder.Recorder;
import ctbrec.recorder.download.StreamSource;
import ctbrec.sites.Site;

public class UnknownModel extends AbstractModel {
    @Override
    public boolean isOnline(boolean ignoreCache) throws IOException, ExecutionException, InterruptedException {
        return false;
    }

    @Override
    public List<StreamSource> getStreamSources() throws IOException, ExecutionException, ParseException, PlaylistException {
        return Collections.emptyList();
    }

    @Override
    public void invalidateCacheEntries() {
    }

    @Override
    public void receiveTip(Double tokens) throws IOException {
    }

    @Override
    public int[] getStreamResolution(boolean failFast) throws ExecutionException {
        return new int[2];
    }

    @Override
    public boolean follow() throws IOException {
        return false;
    }

    @Override
    public boolean unfollow() throws IOException {
        return false;
    }

    @Override
    public Site getSite() {
        return new Site() {

            @Override
            public boolean supportsTips() {
                return false;
            }

            @Override
            public boolean supportsSearch() {
                return false;
            }

            @Override
            public boolean supportsFollow() {
                return false;
            }

            @Override
            public void shutdown() {
            }

            @Override
            public void setRecorder(Recorder recorder) {
            }

            @Override
            public void setEnabled(boolean enabled) {
            }

            @Override
            public boolean searchRequiresLogin() {
                return false;
            }

            @Override
            public List<Model> search(String q) throws IOException, InterruptedException {
                return Collections.emptyList();
            }

            @Override
            public boolean login() throws IOException {
                return false;
            }

            @Override
            public boolean isSiteForModel(Model m) {
                return false;
            }

            @Override
            public boolean isEnabled() {
                return false;
            }

            @Override
            public void init() throws IOException {
            }

            @Override
            public Double getTokenBalance() throws IOException {
                return 0d;
            }

            @Override
            public Recorder getRecorder() {
                return null;
            }

            @Override
            public String getName() {
                return "unknown";
            }

            @Override
            public HttpClient getHttpClient() {
                return null;
            }

            @Override
            public String getBuyTokensLink() {
                return "";
            }

            @Override
            public String getBaseUrl() {
                return "";
            }

            @Override
            public String getAffiliateLink() {
                return "";
            }

            @Override
            public boolean credentialsAvailable() {
                return false;
            }

            @Override
            public Model createModelFromUrl(String url) {
                return null;
            }

            @Override
            public Model createModel(String name) {
                return null;
            }

            @Override
            public String getTitle() {
                return getName();
            }

            @Override
            public void setConfig(Config config) {
                // noop
            }
        };
    }
}