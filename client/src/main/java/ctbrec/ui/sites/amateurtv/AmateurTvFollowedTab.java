package ctbrec.ui.sites.amateurtv;

import ctbrec.sites.Site;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;

public class AmateurTvFollowedTab extends ThumbOverviewTab implements FollowedTab {

    public AmateurTvFollowedTab(String title, PaginatedScheduledService updateService, Site site) {
        super(title, updateService, site);
    }
}
