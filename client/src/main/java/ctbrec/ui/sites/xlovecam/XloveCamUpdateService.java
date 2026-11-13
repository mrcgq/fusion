package ctbrec.ui.sites.xlovecam;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ctbrec.Model;
import ctbrec.sites.xlovecam.XloveCam;
import ctbrec.sites.xlovecam.XloveCamModelLoader;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;

public class XloveCamUpdateService extends PaginatedScheduledService {

    private XloveCamModelLoader loader;
    private Map<String, String> filter;

    public XloveCamUpdateService(XloveCam xloveCam, Map<String, String> filter) {
        this.loader = new XloveCamModelLoader(xloveCam);
        this.filter = new HashMap<>(filter);
        this.filter.putAll(Map.of( // @formatter:off
                "config[nickname]", "",
                "config[favorite]", "0",
                "config[recent]", "0",
                "config[vip]", "0",
                "origin", "postop-eol",
                "stat", "0"
                )); // @formatter:on

    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<List<Model>>() {
            @Override
            public List<Model> call() throws IOException {
                return loader.loadModelList(page, filter);
            }
        };
    }
}
