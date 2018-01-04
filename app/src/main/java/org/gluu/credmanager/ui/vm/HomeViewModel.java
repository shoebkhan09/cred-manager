/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.WebUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.ClientInfoEvent;

import java.time.ZoneOffset;

/**
 * Created by jgomer on 2017-09-08.
 * This class is only utilized to set the user's session UTC offset (this is needed for properly showing dates in local time)
 */
public class HomeViewModel {

    private Logger logger = LogManager.getLogger(getClass());

    //Sets the user location offset
    @Command
    public void onClientInfo(@BindingParam("evt") ClientInfoEvent evt) {

        Session se= Sessions.getCurrent();

        if (WebUtils.getUserOffset(se)==null){
            /*
              One might think of employing evt.getTimeZone() however, such timezone is not the real one but one created
              from the user's browser offset. Thus, it's not associated to something like "America/Los_Angeles" but a
              raw offset like "GMT+0010"
             */
            int offset=evt.getTimeZone().getRawOffset()/1000;
            ZoneOffset zoffset=ZoneOffset.ofTotalSeconds(offset);
            WebUtils.setUserOffset(se, zoffset);

            logger.info(Labels.getLabel("app.user_offset"), zoffset.toString());
            //reloads this page so the navigation flow proceeds (see HomeInitiator class)
            Executions.sendRedirect(null);
        }

    }

}
