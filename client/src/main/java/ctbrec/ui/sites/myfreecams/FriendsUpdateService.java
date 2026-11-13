package ctbrec.ui.sites.myfreecams;

import ctbrec.Config;
import ctbrec.Model;
import ctbrec.StringUtil;
import ctbrec.sites.mfc.*;
import ctbrec.ui.SiteUiFactory;
import ctbrec.ui.tabs.PaginatedScheduledService;
import javafx.concurrent.Task;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ctbrec.io.HttpConstants.REFERER;
import static ctbrec.io.HttpConstants.USER_AGENT;

public class FriendsUpdateService extends PaginatedScheduledService {

    private static final Logger LOG = LoggerFactory.getLogger(FriendsUpdateService.class);
    private final MyFreeCams myFreeCams;
    private Mode mode = Mode.ONLINE;

    public enum Mode {
        ONLINE, OFFLINE
    }

    public FriendsUpdateService(MyFreeCams myFreeCams) {
        this.myFreeCams = myFreeCams;
    }

    @Override
    protected Task<List<Model>> createTask() {
        return new Task<>() {
            @Override
            public List<Model> call() throws IOException {
                if (StringUtil.isBlank(ctbrec.Config.getInstance().getSettings().mfcUsername)) {
                    return Collections.emptyList();
                } else {
                    List<MyFreeCamsModel> models = new ArrayList<>();
                    SiteUiFactory.getUi(myFreeCams).login();
                    String url = myFreeCams.getBaseUrl() + "/php/manage_lists2.php?passcode=&list_type=friends&data_mode=online&get_user_list=1";
                    Request req = new Request.Builder().url(url)
                            .header(USER_AGENT, Config.getInstance().getSettings().httpUserAgent)
                            .header(REFERER, myFreeCams.getBaseUrl())
                            .build();
                    try (Response resp = myFreeCams.getHttpClient().execute(req)) {
                        if (resp.isSuccessful()) {
                            var body = resp.body().string().substring(4);
                            parseModels(body, models);
                        } else {
                            LOG.error("Couldn't load friends list {} {}", resp.code(), resp.message());
                        }
                    }
                    boolean filterOnline = mode == Mode.ONLINE;
                    return models.stream()
                            .filter(m -> {
                                try {
                                    return m.isOnline() == filterOnline;
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                    return false;
                                } catch (Exception e) {
                                    return false;
                                }
                            })
                            .sorted((m1, m2) -> (int) (m2.getCamScore() - m1.getCamScore()))
                            .skip((page - 1) * 50L)
                            .limit(50)
                            .map(Model.class::cast)
                            .toList();
                }
            }

        };
    }

    private void parseModels(String body, List<MyFreeCamsModel> models) {
        try {
            var json = new JSONObject(body);
            for (String key : json.keySet()) {
                var uid = Integer.parseInt(key);
                MyFreeCamsModel model = MyFreeCamsClient.getInstance().getModel(uid);
                if (model == null) {
                    var modelObject = json.getJSONObject(key);
                    var name = modelObject.getString("u");
                    model = myFreeCams.createModel(name);
                    var st = new SessionState();
                    st.setM(new ctbrec.sites.mfc.Model());
                    st.getM().setCamscore(0.0);
                    st.setU(new User());
                    st.setUid(uid);
                    st.setLv(modelObject.getInt("lv"));
                    st.setVs(127);
                }
                models.add(model);
            }
        } catch (Exception e) {
            LOG.info("Exception getting friends list. Response was: {}", body, e);
        }
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }
}
