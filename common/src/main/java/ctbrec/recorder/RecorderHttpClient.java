package ctbrec.recorder;

import java.io.IOException;

import ctbrec.Config;
import ctbrec.io.HttpClient;

public class RecorderHttpClient extends HttpClient {

    public RecorderHttpClient(Config config) {
        super("recorder", config);
    }

    @Override
    public boolean login() throws IOException {
        return false;
    }
}
