package ctbrec.ui.sites.stripchat;

import ctbrec.StringUtil;
import ctbrec.ui.tabs.PaginatedScheduledService;
import org.json.JSONObject;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public abstract class AbstractStripchatUpdateService extends PaginatedScheduledService {

    protected static final Random RNG = new Random();

    protected String getPreviewUrl(JSONObject model) {
        long id = model.optLong("id");
        long timestamp = model.optLong("snapshotTimestamp");
        if (timestamp == 0) {
            return model.optString("previewUrlThumbBig");
        }
        return MessageFormat.format("https://img.strpst.com/thumbs/{0}/{1}", String.valueOf(timestamp), String.valueOf(id));
    }

    protected List<String> createTags(JSONObject model) {
        List<String> tags = new ArrayList<>();
        if (model.optBoolean("isHd")) {
            tags.add("HD");
        }
        if (model.optBoolean("isVr")) {
            tags.add("VR");
        }
        if (model.optBoolean("isLovense")) {
            tags.add("lovense");
        }
        if (model.optBoolean("isNew")) {
            tags.add("new");
        }
        if (model.optBoolean("isMobile")) {
            tags.add("mobile");
        }
        if (model.optBoolean("isNonNude")) {
            tags.add("non-nude");
        }
        if (StringUtil.isNotBlank(model.optString("country"))) {
            tags.add(model.getString("country").toUpperCase());
        }
        if (StringUtil.isNotBlank(model.optString("broadcastGender"))) {
            tags.add(model.getString("broadcastGender"));
        }
        if (StringUtil.isNotBlank(model.optString("status"))) {
            tags.add(model.getString("status"));
        }
        return tags;
    }

    protected String getUniq() {
        String dict = "0123456789abcdefghijklmnopqrstuvwxyz";
        char[] text = new char[16];
        for (int i = 0; i < 16; i++) {
            text[i] = dict.charAt(RNG.nextInt(dict.length()));
        }
        return new String(text);
    }
}
