package ctbrec.ui.sites.myfreecams;

import ctbrec.Model;
import javafx.concurrent.Task;

import java.util.List;

public class NewModelService extends MyFreeCamsAbstractUpdateService {

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() {
                return NewModelService.super.getModels().stream()
                        .filter(m -> m.getPreview() != null)
                        .filter(m -> m.getStreamUrl() != null)
                        .filter(m -> {
                            try {
                                return m.isNew();
                            } catch (Exception e) {
                                return false;
                            }
                        })
                        .sorted((m1, m2) -> m2.getViewerCount() - m1.getViewerCount())
                        .skip((page - 1) * (long) MODELS_PER_PAGE)
                        .limit(MODELS_PER_PAGE)
                        .map(Model.class::cast)
                        .toList();
            }
        };
    }
}
