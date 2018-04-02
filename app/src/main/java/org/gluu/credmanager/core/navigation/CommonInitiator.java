/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core.navigation;

import org.gluu.credmanager.core.ConfigurationHandler;
import org.gluu.credmanager.misc.AppStateEnum;
import org.gluu.credmanager.misc.Utils;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Page;

/**
 * @author jgomer
 */
public class CommonInitiator {

    public void init(Page page) {

        AppStateEnum state = Utils.managedBean(ConfigurationHandler.class).getAppState();
        state = state == null ? AppStateEnum.LOADING : state;
        String err = Labels.getLabel("general.error.general");

        switch (state) {
            case LOADING:
                setPageErrors(page, err, "Credential manager is still starting. Try accessing again in a few seconds.");
                break;
            case FAIL:
                setPageErrors(page, err, "Credential manager did not start properly. Contact your admin.");
                break;
            default:
                //This attrib should be in the session, but it's more comfortable at the desktop level for testing purposes
                page.getDesktop().setAttribute("onMobile", Executions.getCurrent().getBrowser("mobile") != null);
        }


    }

    public void setPageErrors(Page page, String error, String description) {
        page.setAttribute("error", error);
        page.setAttribute("description", description);
    }

}
