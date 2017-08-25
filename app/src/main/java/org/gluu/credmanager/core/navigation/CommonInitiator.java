package org.gluu.credmanager.core.navigation;

import org.gluu.credmanager.core.WebUtils;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;

/**
 * Created by jgomer on 2017-07-20.
 */
public class CommonInitiator {

    public void init(Page page){
        page.setAttribute("onMobile", Executions.getCurrent().getBrowser("mobile") != null);
        page.setAttribute("u2fSupported", WebUtils.u2fSupportedBrowser(Executions.getCurrent().getUserAgent()));
        //WebUtils.u2fSupportedBrowser(WebUtils.getRequestHeader("User-Agent"))
    }

    public void setPageErrors(Page page, String error, String description){
        page.setAttribute("error", error);
        page.setAttribute("description", description);
    }

}
