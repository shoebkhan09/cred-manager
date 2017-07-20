package org.gluu.credmanager.core.init;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zkoss.zk.ui.WebApp;
import org.zkoss.zk.ui.util.WebAppInit;

/**
 * Created by jgomer on 2017-07-04.
 *
 * Apparently you cannot use injection within this class...
 */
public class AppInitializer implements WebAppInit {

    //Logger logger = LogManager.getLogger(getClass());

    public void init(WebApp webApp) throws Exception{

        webApp.getAttributes().put("appName", webApp.getAppName());
        ContextListener listener=(ContextListener)webApp.getServletContext().getAttribute(ContextListener.SELF_ATTRIBUTE);
        listener.proceed();

    }

}
