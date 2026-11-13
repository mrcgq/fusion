package ctbrec.ui.sites.secretfriends;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.sites.secretfriends.SecretFriends;
import ctbrec.sites.secretfriends.SecretFriendsModelParser;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

import static ctbrec.ErrorMessages.HTTP_RESPONSE_BODY_IS_NULL;
import static ctbrec.io.HttpConstants.*;

public class SecretFriendsUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(SecretFriendsUpdateService.class);

    private final String url;
    private final boolean loginRequired;
    private final SecretFriends site;

    public SecretFriendsUpdateService(String url, boolean loginRequired, SecretFriends site) {
        this.url = url;
        this.loginRequired = loginRequired;
        this.site = site;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (loginRequired && !site.credentialsAvailable()) {
                    return Collections.emptyList();
                } else {
                    String paginatedUrl = url;
                    if (page > 1) {
                        String pager = (url.indexOf("/users") > 0) ? "Friend_page" : "AModel_page";
                        paginatedUrl = HttpUrl.parse(url).newBuilder().addQueryParameter(pager, String.valueOf(page)).build().toString();
                    }
                    LOG.debug("Fetching page {}", paginatedUrl);
                    if (loginRequired) {
                        SiteUiFactory.getUi(site).login();
                    }
                    Request request = new Request.Builder()
                            .url(paginatedUrl)
                            .header(ACCEPT, "*/*")
                            .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header(REFERER, SecretFriends.BASE_URI)
                            .build();
                    try (Response response = site.getHttpClient().execute(request)) {
                        if (response.isSuccessful()) {
                            return parseModels(Objects.requireNonNull(response.body(), HTTP_RESPONSE_BODY_IS_NULL).string());
                        } else {
                            throw new HttpException(response.code(), response.message());
                        }
                    }
                }

            }
        };
    }

    private List<Model> parseModels(String body) {
        List<Model> models = new ArrayList<>();
        Elements modelDivs = HtmlParser.getTags(body, "div[class~=model-wrapper]");
        LOG.debug("Found {} models", modelDivs.size());
        for (Element div : modelDivs) {
            try {
                models.add(SecretFriendsModelParser.parse(site, div));
            } catch (Exception e) {
                LOG.warn("Couldn't parse one of the models: {}", div.html(), e);
            }
        }
        return models;
    }


}
