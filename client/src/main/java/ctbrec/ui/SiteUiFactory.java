package ctbrec.ui;

import ctbrec.sites.Site;
import ctbrec.sites.amateurtv.AmateurTv;
import ctbrec.sites.bonga.BongaCams;
import ctbrec.sites.cam4.Cam4;
import ctbrec.sites.camsoda.Camsoda;
import ctbrec.sites.chaturbate.Chaturbate;
import ctbrec.sites.cherrytv.CherryTv;
import ctbrec.sites.dreamcam.Dreamcam;
import ctbrec.sites.fc2live.Fc2Live;
import ctbrec.sites.flirt4free.Flirt4Free;
import ctbrec.sites.jasmin.LiveJasmin;
import ctbrec.sites.manyvids.MVLive;
import ctbrec.sites.mfc.MyFreeCams;
import ctbrec.sites.secretfriends.SecretFriends;
import ctbrec.sites.showup.Showup;
import ctbrec.sites.streamate.Streamate;
import ctbrec.sites.streamray.Streamray;
import ctbrec.sites.stripchat.Stripchat;
import ctbrec.sites.winktv.WinkTv;
import ctbrec.sites.xlovecam.XloveCam;
import ctbrec.ui.sites.amateurtv.AmateurTvSiteUi;
import ctbrec.ui.sites.bonga.BongaCamsSiteUi;
import ctbrec.ui.sites.cam4.Cam4SiteUi;
import ctbrec.ui.sites.camsoda.CamsodaSiteUi;
import ctbrec.ui.sites.chaturbate.ChaturbateSiteUi;
import ctbrec.ui.sites.cherrytv.CherryTvSiteUi;
import ctbrec.ui.sites.dreamcam.DreamcamSiteUi;
import ctbrec.ui.sites.fc2live.Fc2LiveSiteUi;
import ctbrec.ui.sites.flirt4free.Flirt4FreeSiteUi;
import ctbrec.ui.sites.jasmin.LiveJasminSiteUi;
import ctbrec.ui.sites.manyvids.MVLiveSiteUi;
import ctbrec.ui.sites.myfreecams.MyFreeCamsSiteUi;
import ctbrec.ui.sites.secretfriends.SecretFriendsSiteUi;
import ctbrec.ui.sites.showup.ShowupSiteUi;
import ctbrec.ui.sites.streamate.StreamateSiteUi;
import ctbrec.ui.sites.streamray.StreamraySiteUi;
import ctbrec.ui.sites.stripchat.StripchatSiteUi;
import ctbrec.ui.sites.winktv.WinkTvSiteUi;
import ctbrec.ui.sites.xlovecam.XloveCamSiteUi;

public class SiteUiFactory {

    private static AmateurTvSiteUi amateurTvUi;
    private static BongaCamsSiteUi bongaSiteUi;
    private static Cam4SiteUi cam4SiteUi;
    private static CamsodaSiteUi camsodaSiteUi;
    private static ChaturbateSiteUi ctbSiteUi;
    private static CherryTvSiteUi cherryTvSiteUi;
    private static Fc2LiveSiteUi fc2SiteUi;
    private static Flirt4FreeSiteUi flirt4FreeSiteUi;
    private static LiveJasminSiteUi jasminSiteUi;
    private static MVLiveSiteUi mvLiveSiteUi;
    private static MyFreeCamsSiteUi mfcSiteUi;
    private static SecretFriendsSiteUi secretFriendsSiteUi;
    private static ShowupSiteUi showupSiteUi;
    private static StreamateSiteUi streamateSiteUi;
    private static StripchatSiteUi stripchatSiteUi;
    private static XloveCamSiteUi xloveCamSiteUi;
    private static StreamraySiteUi streamraySiteUi;
    private static WinkTvSiteUi winkTvSiteUi;
    private static DreamcamSiteUi dreamcamSiteUi;

    private SiteUiFactory() {
    }

    public static synchronized SiteUI getUi(Site site) { // NOSONAR
        if (site instanceof AmateurTv) {
            if (amateurTvUi == null) {
                amateurTvUi = new AmateurTvSiteUi((AmateurTv) site);
            }
            return amateurTvUi;
        } else if (site instanceof BongaCams) {
            if (bongaSiteUi == null) {
                bongaSiteUi = new BongaCamsSiteUi((BongaCams) site);
            }
            return bongaSiteUi;
        } else if (site instanceof Cam4) {
            if (cam4SiteUi == null) {
                cam4SiteUi = new Cam4SiteUi((Cam4) site);
            }
            return cam4SiteUi;
        } else if (site instanceof Camsoda) {
            if (camsodaSiteUi == null) {
                camsodaSiteUi = new CamsodaSiteUi((Camsoda) site);
            }
            return camsodaSiteUi;
        } else if (site instanceof Chaturbate) {
            if (ctbSiteUi == null) {
                ctbSiteUi = new ChaturbateSiteUi((Chaturbate) site);
            }
            return ctbSiteUi;
        } else if (site instanceof CherryTv) {
            if (cherryTvSiteUi == null) {
                cherryTvSiteUi = new CherryTvSiteUi((CherryTv) site);
            }
            return cherryTvSiteUi;
        } else if (site instanceof Dreamcam) {
            if (dreamcamSiteUi == null) {
                dreamcamSiteUi = new DreamcamSiteUi((Dreamcam) site);
            }
            return dreamcamSiteUi;
        } else if (site instanceof Fc2Live) {
            if (fc2SiteUi == null) {
                fc2SiteUi = new Fc2LiveSiteUi((Fc2Live) site);
            }
            return fc2SiteUi;
        } else if (site instanceof Flirt4Free) {
            if (flirt4FreeSiteUi == null) {
                flirt4FreeSiteUi = new Flirt4FreeSiteUi((Flirt4Free) site);
            }
            return flirt4FreeSiteUi;
        } else if (site instanceof MVLive) {
            if (mvLiveSiteUi == null) {
                mvLiveSiteUi = new MVLiveSiteUi((MVLive) site);
            }
            return mvLiveSiteUi;
        } else if (site instanceof MyFreeCams) {
            if (mfcSiteUi == null) {
                mfcSiteUi = new MyFreeCamsSiteUi((MyFreeCams) site);
            }
            return mfcSiteUi;
        } else if (site instanceof SecretFriends) {
            if (secretFriendsSiteUi == null) {
                secretFriendsSiteUi = new SecretFriendsSiteUi((SecretFriends) site);
            }
            return secretFriendsSiteUi;
        } else if (site instanceof Showup) {
            if (showupSiteUi == null) {
                showupSiteUi = new ShowupSiteUi((Showup) site);
            }
            return showupSiteUi;
        } else if (site instanceof Streamate) {
            if (streamateSiteUi == null) {
                streamateSiteUi = new StreamateSiteUi((Streamate) site);
            }
            return streamateSiteUi;
        } else if (site instanceof LiveJasmin) {
            if (jasminSiteUi == null) {
                jasminSiteUi = new LiveJasminSiteUi((LiveJasmin) site);
            }
            return jasminSiteUi;
        } else if (site instanceof Stripchat) {
            if (stripchatSiteUi == null) {
                stripchatSiteUi = new StripchatSiteUi((Stripchat) site);
            }
            return stripchatSiteUi;
        } else if (site instanceof Streamray) {
            if (streamraySiteUi == null) {
                streamraySiteUi = new StreamraySiteUi((Streamray) site);
            }
            return streamraySiteUi;
        } else if (site instanceof WinkTv) {
            if (winkTvSiteUi == null) {
                winkTvSiteUi = new WinkTvSiteUi((WinkTv) site);
            }
            return winkTvSiteUi;
        } else if (site instanceof XloveCam) {
            if (xloveCamSiteUi == null) {
                xloveCamSiteUi = new XloveCamSiteUi((XloveCam) site);
            }
            return xloveCamSiteUi;
        }
        throw new RuntimeException("Unknown site " + site.getName());
    }

}
