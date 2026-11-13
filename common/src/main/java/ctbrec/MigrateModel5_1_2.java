package ctbrec;

import lombok.experimental.UtilityClass;
import org.json.JSONObject;

@UtilityClass
public class MigrateModel5_1_2 {

    private static final String MARKED_FOR_LATER = "markedForLater";
    private static final String ADDED_TIMESTAMP = "addedTimestamp";
    private static final String URL = "url";

    public static void migrate(JSONObject model) {
        if (model.has(URL)) {
            model.put(URL, model.getString(URL).replace(" ", "%20"));
        }
        if (model.has(MARKED_FOR_LATER)) {
            model.put("bookmarked", model.getBoolean(MARKED_FOR_LATER));
            model.remove(MARKED_FOR_LATER);
        }
        if (model.has(ADDED_TIMESTAMP)) {
            model.put("addedAt", model.optLong(ADDED_TIMESTAMP));
            model.remove(ADDED_TIMESTAMP);
        }
    }
}
