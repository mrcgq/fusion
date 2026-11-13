package ctbrec.ui.sites.camsoda;

import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.camsoda.CamsodaModel;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class CamsodaFollowedUpdateService extends CamsodaUpdateService {

    public CamsodaFollowedUpdateService(String url, boolean loginRequired, Camsoda camsoda, Predicate<CamsodaModel> filter) {
        super(url, loginRequired, camsoda, filter);
    }

    protected List<CamsodaModel> parseModels(String body) {
        List<CamsodaModel> models = new ArrayList<>();
        var json = new JSONObject(body);
        var userList = json.getJSONArray("userList");
        for (var i = 0; i < userList.length(); i++) {
            var jsonModel = userList.getJSONObject(i);
            CamsodaModel model = (CamsodaModel) camsoda.createModel(jsonModel.getString("username"));
            model.setDisplayName(jsonModel.optString("displayName"));
            model.setPreview(jsonModel.optString("thumbUrl"));
            model.setDescription(jsonModel.optString("subjectText"));
            model.setOnlineStateByStatus(jsonModel.optString("status"));
            models.add(model);
        }
        return models;
    }
}
