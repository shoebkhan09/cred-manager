package org.gluu.credmanager.core.navigation;

import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Initiator;

import java.util.Map;

/**
 * Created by jgomer on 2017-07-19.
 */
public class PageInitiator implements Initiator {

    private boolean checkAdminRights;

    public PageInitiator(boolean adminRights) {
        checkAdminRights = adminRights;
    }

    public PageInitiator() {
    }

    public void doInit(Page page, Map<String, Object> map) throws Exception {

        Session se= Sessions.getCurrent();
        User user= WebUtils.getUser(se);
        if (user==null || (checkAdminRights && !user.isAdmin()))
            throw new Exception(Labels.getLabel("usr.not_authorized"));

    }

}