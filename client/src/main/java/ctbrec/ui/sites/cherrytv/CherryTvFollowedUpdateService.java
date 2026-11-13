package ctbrec.ui.sites.cherrytv;

import ctbrec.Model;
import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.sites.cherrytv.CherryTvModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static ctbrec.Model.State.OFFLINE;
import static ctbrec.Model.State.ONLINE;

public class CherryTvFollowedUpdateService extends CherryTvUpdateService {

    private static final Logger LOG = LoggerFactory.getLogger(CherryTvFollowedUpdateService.class);

    public CherryTvFollowedUpdateService(CherryTv site) {
        super("following", site, true);
        url = "https://api.cherry.tv/graphql?operationName=findFollowingBroadcastsByPage&variables={\"limit\":1000}&extensions={\"persistedQuery\":{\"version\":1,\"sha256Hash\":\"241ae6ae3c2bd62e78432b4d51a92a1baa59d9e94d173867a2a45586704465d1\"}}";
    }

    @Override
    protected List<Model> parseModels(String body) throws IOException {
        var json = new JSONObject(body);
        //LOG.debug(json.toString(2));
        if (json.has("errors")) {
            JSONArray errors = json.getJSONArray("errors");
            JSONObject first = errors.getJSONObject(0);
            throw new IOException(first.getString("message"));
        }
        List<Model> models = new ArrayList<>();
        try {
            JSONArray followings = json.getJSONObject("data").getJSONObject("broadcasts").getJSONArray("broadcasts");
            for (int i = 0; i < followings.length(); i++) {
                JSONObject following = followings.getJSONObject(i);
                CherryTvModel model = site.createModel(following.optString("username"));
                model.setId(following.getString("id"));
                model.setPreview(following.optString("imageUrl"));
                var online = following.optString("broadcastStatus").equalsIgnoreCase("Live");
                model.setOnline(online);
                model.setOnlineState(online ? ONLINE : OFFLINE);
                models.add(model);
            }
        } catch (JSONException e) {
            LOG.error("Couldn't parse JSON, the structure might have changed", e);
        }
        return models;
    }
}
