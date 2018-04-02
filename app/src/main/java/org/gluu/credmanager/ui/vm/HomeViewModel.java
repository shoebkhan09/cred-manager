/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm;

import org.gluu.credmanager.core.SessionContext;
import org.gluu.credmanager.core.UserService;
import org.gluu.credmanager.core.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.ClientInfoEvent;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.time.ZoneOffset;

/**
 * This class is only utilized to set the user's session UTC offset (this is needed for properly showing dates in local time)
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class HomeViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private SessionContext sessionContext;

    @WireVariable
    private UserService userService;

    //Sets the user location offset
    @Command
    public void onClientInfo(@BindingParam("evt") ClientInfoEvent evt) {

        if (sessionContext.getZoneOffset() == null) {
            /*
              One might think of employing evt.getTimeZone() however, such timezone is not the real one but one created
              from the user's browser offset. Thus, it's not associated to something like "America/Los_Angeles" but a
              raw offset like "GMT+0010"
             */
            int offset = evt.getTimeZone().getRawOffset() / 1000;
            ZoneOffset zoffset = ZoneOffset.ofTotalSeconds(offset);
            sessionContext.setZoneOffset(zoffset);

            logger.info("Time offset for session is {}", zoffset.toString());
            //reloads this page so the navigation flow proceeds (see HomeInitiator class)
            Executions.sendRedirect(null);
        }

    }

    @Init
    public void init() {

        //TODO: remove this
        User user = new User();
        user.setGivenName("admin");
        user.setAdmin(true);
        user.setUserName("admin");
        user.setPreferredMethod("twilio_sms");
        user.setId("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!A8F2.DE1E.D7FB");
        //user.setId("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!XXXX.AAAA.1111");
        sessionContext.setUser(user);

        userService.setupRequisites(user.getId());

    }

}
