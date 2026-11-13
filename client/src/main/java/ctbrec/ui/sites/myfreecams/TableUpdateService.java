package ctbrec.ui.sites.myfreecams;

import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.sites.mfc.MyFreeCamsClient;
import ctbrec.sites.mfc.SessionState;
import javafx.concurrent.ScheduledService;
import javafx.concurrent.Task;

import java.util.Collection;

public class TableUpdateService extends ScheduledService<Collection<SessionState>> {

    private final MyFreeCams mfc;

    public TableUpdateService(MyFreeCams mfc) {
        this.mfc = mfc;
    }

    @Override
    protected Task<Collection<SessionState>> createTask() {
        return new Task<>() {
            @Override
            public Collection<SessionState> call() {
                MyFreeCamsClient client = mfc.getClient();
                return client.getSessionStates();
            }
        };
    }

}
