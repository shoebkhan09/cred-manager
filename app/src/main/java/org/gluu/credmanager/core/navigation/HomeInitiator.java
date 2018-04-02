/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core.navigation;

import org.zkoss.zk.ui.Page;
import org.zkoss.zk.ui.util.Initiator;

import java.util.Map;

/**
 * This is a ZK Page Initiator (the doInit method is called before any rendering of UI components). It's the initiator
 * associated to the /index.zul URL (home URL) so here's where the authentication flow is handled.
 * @author jgomer
 */
public class HomeInitiator extends CommonInitiator implements Initiator {

    public void doInit(Page page, Map<String, Object> map) {
        init(page);
    }

}
