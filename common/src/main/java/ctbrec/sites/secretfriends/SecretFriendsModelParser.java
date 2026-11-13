package ctbrec.sites.secretfriends;

import ctbrec.Model;
import org.jsoup.nodes.Element;

import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecretFriendsModelParser {
    private SecretFriendsModelParser() {
    }

    public static SecretFriendsModel parse(SecretFriends site, Element modelWrapper) {
        String name = parseName(modelWrapper);
        SecretFriendsModel model = site.createModel(name);
        model.setPreview(parsePreview(modelWrapper));
        model.setOnlineState(extractOnlineState(modelWrapper));
        return model;
    }

    private static String parsePreview(Element div) {
        Element wrapper = div.selectFirst("div.placeholder-wrapper");
        if (wrapper == null) {
            return null;
        }
        String style = wrapper.attr("style");
        Pattern p = Pattern.compile("background-image: url\\('(.*?)'\\)");
        Matcher m = p.matcher(style);
        if (m.find()) {
            return m.group(1);
        } else {
            return null;
        }
    }

    private static String parseName(Element div) {
        Element bioLink = Objects.requireNonNull(div.selectFirst("a[href*=/friend]"), "a[href*=/friend] not found");
        bioLink.setBaseUri(SecretFriends.BASE_URI);
        String href = bioLink.attr("href");
        if (href.contains("signup")) {
            return href.substring(href.indexOf('=') + 1);
        } else {
            String name = href.substring(href.lastIndexOf('/') + 1);
            if (name.indexOf('?') >= 0) {
                name = name.substring(0, name.indexOf('?'));
            }
            if (name.indexOf('#') >= 0) {
                name = name.substring(0, name.indexOf('#'));
            }
            return name;
        }
    }

    private static Model.State extractOnlineState(Element div) {
        Element modelTag = Objects.requireNonNull(div.selectFirst("div[class~=model-tag]"), "div.model-tag not found");
        Set<String> cssClasses = modelTag.classNames();
        for (String cssClass : cssClasses) {
            switch (cssClass) {
                case "model-online":
                    return Model.State.ONLINE;
                case "model-private":
                case "model-show":
                case "model-vip":
                    return Model.State.PRIVATE;
                case "model-offline":
                    return Model.State.OFFLINE;
                default:
                    // keep going
            }
        }
        return Model.State.OFFLINE;
    }
}
