package ctbrec.ui.sites.showup;

import ctbrec.Model;
import ctbrec.sites.showup.Showup;
import ctbrec.sites.showup.ShowupHttpClient;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ShowupUpdateService extends PaginatedScheduledService {

    private final Showup showup;
    private final String category;
    protected int modelsPerPage = 48;

    public ShowupUpdateService(Showup showup, String category) {
        this.showup = showup;
        this.category = category;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                return getModelList().stream()
                        .skip((page - 1) * (long) modelsPerPage)
                        .limit(modelsPerPage)
                        .collect(Collectors.toList()); // NOSONAR
            }
        };
    }

    private List<Model> getModelList() throws IOException {
        ShowupHttpClient httpClient = (ShowupHttpClient) showup.getHttpClient();
        httpClient.setCookie("category", category);
        var modelsList = showup.getModelList(true);
        if (modelsList == null) {
            return Collections.emptyList();
        }
        return modelsList;
    }

}
