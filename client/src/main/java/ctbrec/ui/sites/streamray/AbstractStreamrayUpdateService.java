package ctbrec.ui.sites.streamray;

import ctbrec.sites.streamray.Streamray;
import ctbrec.ui.tabs.PaginatedScheduledService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.json.JSONArray;

import java.net.URLEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

@RequiredArgsConstructor
public abstract class AbstractStreamrayUpdateService extends PaginatedScheduledService {

    @Getter
    @Setter
    private static JSONArray mapping;
    protected static final int MODELS_PER_PAGE = 48;
    protected static final String API_URL = "https://beta-api.cams.com/won/compressed/";
    protected final Streamray site;

    protected String getPreviewURL(String name) {
        String lname = name.toLowerCase();
        String url = MessageFormat.format("https://images4.streamray.com/images/streamray/won/jpg/{0}/{1}/{2}_640.jpg", lname.substring(0, 1), lname.substring(lname.length() - 1), lname);
        try {
            return MessageFormat.format("https://dynimages.securedataimages.com/unsigned/rs:fill:640::0/g:no/plain/{0}@jpg", URLEncoder.encode(url, UTF_8));
        } catch (Exception ex) {
            return url;
        }
    }

    protected List<String> createTags(JSONArray m) {
        List<String> tags = new ArrayList<>();
        int idx1 = mappingIndex("gender");
        switch (m.optString(idx1)) {
            case "M" -> tags.add("male");
            case "F" -> tags.add("female");
            case "TS" -> tags.add("trans");
            default -> {
                // don't add anything
            }
        }
        int idx2 = mappingIndex("ethnicity");
        switch (m.optString(idx2)) {
            case "02" -> tags.add("asian");
            case "03" -> tags.add("ebony");
            case "04" -> tags.add("white");
            case "05" -> tags.add("indian");
            case "06" -> tags.add("latina");
            case "07" -> tags.add("middle-eastern");
            default -> {
                // don't add anything
            }
        }
        int idx3 = mappingIndex("hair_color");
        switch (m.optString(idx3)) {
            case "01" -> tags.add("black-hair");
            case "02" -> tags.add("blonde");
            case "03" -> tags.add("brunette");
            case "06" -> tags.add("redhead");
            default -> {
                // don't add anything
            }
        }
        int idx4 = mappingIndex("chat_type");
        switch (m.optString(idx4)) {
            case "0" -> tags.add("offline");
            case "1" -> tags.add("public");
            case "2" -> tags.add("nude-show");
            case "3" -> tags.add("private");
            case "4" -> tags.add("exclusive");
            case "6" -> tags.add("ticket-show");
            case "7" -> tags.add("voyeur");
            case "10" -> tags.add("party");
            case "13" -> tags.add("group");
            case "14" -> tags.add("c2c");
            default -> {
                // don't add anything
            }
        }
        return tags;
    }

    protected int mappingIndex(String s) {
        for (var i = 0; i < mapping.length(); i++) {
            if (Objects.equals(s, mapping.get(i))) return i;
        }
        return -1;
    }
}
