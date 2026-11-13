package ctbrec.sites.winktv;

import ctbrec.Config;
import ctbrec.io.HttpClient;

import java.io.IOException;

public class WinkTvHttpClient extends HttpClient {

    public WinkTvHttpClient(Config config) {
        super("winktv", config);
    }

    @Override
    public boolean login() throws IOException {
        return false;
    }
}
