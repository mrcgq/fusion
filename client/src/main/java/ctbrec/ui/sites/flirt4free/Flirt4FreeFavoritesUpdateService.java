package ctbrec.ui.sites.flirt4free;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.io.HtmlParser;
import ctbrec.io.HttpException;
import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.sites.flirt4free.Flirt4FreeModel;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import static ctbrec.io.HttpClient.gunzipBody;
import static ctbrec.io.HttpConstants.*;

public class Flirt4FreeFavoritesUpdateService extends PaginatedScheduledService {
    private final Flirt4Free flirt4free;

    public Flirt4FreeFavoritesUpdateService(Flirt4Free flirt4free) {
        this.flirt4free = flirt4free;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                return loadModelList();
            }
        };
    }

    private List<Model> loadModelList() throws IOException {
        List<Model> models = new ArrayList<>();
        String url = flirt4free.getBaseUrl() + "/my-account/secure/favorites.php?a=models&sort=online&status=all&pg=" + page;
        SiteUiFactory.getUi(flirt4free).login();
        var request = new Request.Builder()
                .url(url)
                .header(ACCEPT, "*/*")
                .header(ACCEPT_LANGUAGE, Locale.ENGLISH.getLanguage())
                .header(ACCEPT_ENCODING, ACCEPT_ENCODING_GZIP)
                .header(REFERER, flirt4free.getBaseUrl())
                .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                .build();
        try (var response = flirt4free.getHttpClient().execute(request)) {
            if (response.isSuccessful()) {
                var body = gunzipBody(response);
                Elements modelContainers = HtmlParser.getTags(body, "div.model-container");
                for (Element modelContainer : modelContainers) {
                    String modelHtml = modelContainer.html();
                    Element bioLink = HtmlParser.getTag(modelHtml, "a.common-link");
                    bioLink.setBaseUri(flirt4free.getBaseUrl());
                    Flirt4FreeModel model = (Flirt4FreeModel) flirt4free.createModelFromUrl(bioLink.absUrl("href"));
                    Element img = HtmlParser.getTag(modelHtml, "a > img[alt]");
                    model.setDisplayName(img.attr("alt"));
                    model.setPreview(img.attr("src"));
                    model.setDescription("");
                    try {
                        model.setOnlineState(model.isOnline() ? Model.State.ONLINE : Model.State.OFFLINE);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } catch (ExecutionException e) {
                        // ignore
                    }
                    models.add(model);
                }
                return models;
            } else {
                throw new HttpException(response.code(), response.message());
            }
        }
    }
}
