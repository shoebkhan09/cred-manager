/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.core.init;

import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.util.WebAppInit;

/**
 * Created by jgomer on 2017-07-04.
 * This class listens when the ZK application is ready. This happens some time after the web application is initialized,
 * that is, after the contextInitialized Java EE event occurs
 */
public class ZKInitializer implements WebAppInit {

    static final String ZK_READY_ATTR="zk-ready";
    //public static final String COUNT_DESKTOPS="desks";

    public void init(WebApp webApp) throws Exception{
        //This attribute is stored here for future use inside zul templates
        webApp.getAttributes().put("appName", webApp.getAppName());
        webApp.getServletContext().setAttribute(ZK_READY_ATTR, true);
    }

}
