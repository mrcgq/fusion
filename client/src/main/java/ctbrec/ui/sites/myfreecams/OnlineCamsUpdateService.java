package ctbrec.ui.sites.myfreecams;


import ctbrec.Model;
import javafx.concurrent.Task;

import java.util.List;

public class OnlineCamsUpdateService extends MyFreeCamsAbstractUpdateService {

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() {

                return OnlineCamsUpdateService.super.getModels().stream()
                        .filter(m -> m.getPreview() != null)
                        .filter(m -> m.getStreamUrl() != null)
                        .filter(m -> {
                            try {
                                return m.isOnline();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return false;
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .sorted((m1, m2) -> (int) (m2.getCamScore() - m1.getCamScore()))
                        .skip((page - 1) * (long) MODELS_PER_PAGE)
                        .limit(MODELS_PER_PAGE)
                        .map(Model.class::cast)
                        .toList();
            }
        };
    }

}
