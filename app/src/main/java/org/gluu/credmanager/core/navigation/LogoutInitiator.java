package org.gluu.credmanager.core.navigation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.WebUtils;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Initiator;

import java.util.Map;

/**
 * Created by jgomer on 2017-07-20.
 */
public class LogoutInitiator extends CommonInitiator implements Initiator {

    private Logger logger = LogManager.getLogger(getClass());

    public void doInit(Page page, Map<String, Object> map){
        init(page);
        //Do session clean up
        WebUtils.purgeSession(Sessions.getCurrent());
    }

}
