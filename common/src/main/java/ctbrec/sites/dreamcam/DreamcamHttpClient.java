package ctbrec.sites.dreamcam;

import ctbrec.Config;
import ctbrec.io.HttpClient;

import java.io.IOException;

public class DreamcamHttpClient extends HttpClient {

    public DreamcamHttpClient(Config config) {
        super("dreamcam", config);
    }

    @Override
    public boolean login() throws IOException {
        return false;
    }
}
