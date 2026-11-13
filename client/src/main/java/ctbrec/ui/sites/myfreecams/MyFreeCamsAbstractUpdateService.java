package ctbrec.ui.sites.myfreecams;

import ctbrec.GlobalThreadPool;
import ctbrec.Model;
import ctbrec.sites.mfc.MyFreeCamsClient;
import ctbrec.sites.mfc.MyFreeCamsModel;
import ctbrec.ui.tabs.PaginatedScheduledService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
public abstract class MyFreeCamsAbstractUpdateService extends PaginatedScheduledService {
    static final int MODELS_PER_PAGE = 50;
    MyFreeCamsClient client = MyFreeCamsClient.getInstance();

    protected List<MyFreeCamsModel> getModels() {
        List<MyFreeCamsModel> models = client.getModels().stream().toList();

        for (Model model : models) {
            GlobalThreadPool.submit(() -> {
                try {
                    client.getSessionState(model);
                    model.isOnline();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    log.debug("Couldn't update model online state", e);
                }
            });
        }
        return models;
    }
}
