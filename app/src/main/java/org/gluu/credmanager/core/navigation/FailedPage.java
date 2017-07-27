package org.gluu.credmanager.core.navigation;

import org.zkoss.zk.ui.Page;

/**
 * Created by jgomer on 2017-07-20.
 */
public class FailedPage {

    public void setPageErrors(Page page, String error, String description){
        page.setAttribute("error", error);
        page.setAttribute("description", description);
    }

}
