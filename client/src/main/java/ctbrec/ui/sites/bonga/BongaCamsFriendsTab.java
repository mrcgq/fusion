package ctbrec.ui.sites.bonga;

import ctbrec.sites.Site;
import ctbrec.ui.tabs.FollowedTab;
import ctbrec.ui.tabs.PaginatedScheduledService;
import ctbrec.ui.tabs.ThumbOverviewTab;

public class BongaCamsFriendsTab extends ThumbOverviewTab implements FollowedTab {

    public BongaCamsFriendsTab(String title, PaginatedScheduledService updateService, Site site) {
        super(title, updateService, site);
    }

}
