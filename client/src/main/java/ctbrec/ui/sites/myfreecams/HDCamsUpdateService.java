package ctbrec.ui.sites.myfreecams;


import ctbrec.Model;
import ctbrec.sites.mfc.SessionState;
import ctbrec.sites.mfc.User;
import javafx.concurrent.Task;

import java.util.List;
import java.util.Optional;

public class HDCamsUpdateService extends MyFreeCamsAbstractUpdateService {

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() {
                return HDCamsUpdateService.super.getModels().stream()
                        .filter(m -> m.getPreview() != null)
                        .filter(m -> m.getStreamUrl() != null)
                        .filter(m -> Optional.ofNullable(client.getSessionState(m))
                                .map(SessionState::getU)
                                .map(User::getPhase)
                                .orElse("").equalsIgnoreCase("a"))
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
                        .skip((page - 1L) * MODELS_PER_PAGE)
                        .limit(MODELS_PER_PAGE)
                        .map(Model.class::cast)
                        .toList();
            }
        };
    }

}
