package org.gluu.credmanager.core.init;

import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.util.WebAppInit;

/**
 * Created by jgomer on 2017-07-04.
 *
 */
public class ZKInitializer implements WebAppInit {

    static final String ZK_READY_ATTR="zk-ready";
    //public static final String COUNT_DESKTOPS="desks";

    public void init(WebApp webApp) throws Exception{
        //This attribute is stored here for future use inside zul templates
        webApp.getAttributes().put("appName", webApp.getAppName());
        //webApp.getAttributes().put(COUNT_DESKTOPS, 0);
        webApp.getServletContext().setAttribute(ZK_READY_ATTR, true);
    }

}
