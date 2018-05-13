/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm.user;

import org.gluu.credmanager.core.SessionContext;
import org.gluu.credmanager.core.UserService;
import org.gluu.credmanager.core.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

/**
 * This is the superclass of all ViewModels associated to zul pages used by regular users of the application
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class UserViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private SessionContext sessionContext;

    @WireVariable
    UserService userService;

    User user;

    @Init
    public void init() {
        user = sessionContext.getUser();
    }

    @Command
    public void logoutFromAuthzServer() {

        try {
            /*
            Session se= Sessions.getCurrent();
            String idToken=WebUtils.getIdToken(se);
            Executions.sendRedirect(services.getOxdService().getLogoutUrl(idToken));
            */
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

}
