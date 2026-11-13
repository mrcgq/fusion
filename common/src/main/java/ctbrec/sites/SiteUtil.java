package ctbrec.sites;

import ctbrec.Model;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Optional;

@UtilityClass
public class SiteUtil {
    public static Optional<Site> getSiteForModel(List<Site> sites, Model model) {
        for (Site site : sites) {
            if (site.isSiteForModel(model)) {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }
}
