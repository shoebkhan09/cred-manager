package org.gluu.credmanager.core.navigation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.core.WebUtils;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;

/**
 * Created by jgomer on 2017-07-20.
 */
public class CommonInitiator {

    private Logger logger = LogManager.getLogger(getClass());

    public void init(Page page){
        Session se=Sessions.getCurrent();

        //If app is not working, just set page error
        if (WebUtils.getServices(se).getAppConfig().isInOperableState()) {
            //This attrib should be in the session, but it's more comfortable at the page level for mobile testing purposes
            //if (se.getAttribute("onMobile")==null)
            page.setAttribute("onMobile", WebUtils.isCurrentBrowserMobile());

            se.setAttribute("custdir", WebUtils.getBrandingPath(se) == null ? "" : AppConfiguration.BASE_URL_BRANDING_PATH);

            if (se.getAttribute("u2fSupported") == null)
                se.setAttribute("u2fSupported", WebUtils.u2fSupportedBrowser(Executions.getCurrent().getUserAgent()));
            //WebUtils.u2fSupportedBrowser(WebUtils.getRequestHeader("User-Agent"))
        }
        else
            setPageErrors(page, Labels.getLabel("general.error.general"), Labels.getLabel("usr.webapp_wrong_state"));

    }

    public void setPageErrors(Page page, String error, String description){
        page.setAttribute("error", error);
        page.setAttribute("description", description);
    }

}
