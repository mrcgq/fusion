package ctbrec.sites.amateurtv;

import static ctbrec.io.HttpConstants.*;

import java.io.IOException;
import java.util.Locale;

import org.json.JSONObject;

import ctbrec.Config;
import ctbrec.io.HttpClient;
import ctbrec.io.HttpException;
import okhttp3.Request;
import okhttp3.Response;

public class AmateurTvHttpClient extends HttpClient {

    public AmateurTvHttpClient(Config config) {
        super("amateurtv", config);
    }

    @Override
    public boolean login() throws IOException {
        return checkLoginSuccess();
    }

    /**
     * Check, if the login worked by requesting the user profile
     *
     * @throws IOException
     */
    public boolean checkLoginSuccess() throws IOException {
        String url = AmateurTv.BASE_URL + "/v3/readmodel/user/me";
        Request request = new Request.Builder()
                .url(url)
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .header(ACCEPT, MIMETYPE_APPLICATION_JSON)
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(REFERER, AmateurTv.BASE_URL)
                .build();
        try (Response response = execute(request)) {
            if (response.isSuccessful()) {
                JSONObject json = new JSONObject(response.body().string());
                return json.has("username") && !json.getString("username").equalsIgnoreCase("guest");
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }
}
