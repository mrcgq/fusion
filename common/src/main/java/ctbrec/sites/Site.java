package ctbrec.sites;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HttpClient;
import ctbrec.recorder.Recorder;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

public interface Site {
    String getTitle();

    String getName();

    String getBaseUrl();

    String getAffiliateLink();

    void setRecorder(Recorder recorder);

    Recorder getRecorder();

    Model createModel(String name);

    Double getTokenBalance() throws IOException;

    String getBuyTokensLink();

    boolean login() throws IOException;

    HttpClient getHttpClient();

    default void init() throws IOException {
        // do nothing per default
    }

    void shutdown();

    boolean supportsTips();

    boolean supportsFollow();

    boolean supportsSearch();

    boolean isSiteForModel(Model m);

    boolean credentialsAvailable();

    void setEnabled(boolean enabled);

    boolean isEnabled();

    List<Model> search(String q) throws IOException, InterruptedException;

    boolean searchRequiresLogin();

    Model createModelFromUrl(String url);

    void setConfig(Config config);

    default Duration getOnlineCheckTimeout() {
        return Duration.ofSeconds(2);
    }
}
