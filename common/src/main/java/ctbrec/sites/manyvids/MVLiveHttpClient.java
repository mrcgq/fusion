package ctbrec.sites.manyvids;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.Request;
import okhttp3.Response;

public class MVLiveHttpClient extends HttpClient {

    public MVLiveHttpClient(Config config) {
        super("mvlive", config);
    }

    @Override
    public boolean login() throws IOException {
        return false;
    }

    public MVLiveHttpClient newSession() {
        MVLiveHttpClient newClient = new MVLiveHttpClient(config);
        newClient.client = newClient.client.newBuilder()
                .cookieJar(createCookieJar())
                .build();
        newClient.reconfigure();
        newClient.cookieJar.clear();
        return newClient;
    }

    public void fetchAuthenticationCookies() throws IOException {
        Request req = new Request.Builder()
                .url("https://www.manyvids.com/tak-live-redirect.php")
                .header(USER_AGENT, config.getSettings().httpUserAgent)
                .build();
        try (Response response = execute(req)) {
            if (!response.isSuccessful()) {
                throw new HttpException(response.code(), response.message());
            }
        }
    }
}
