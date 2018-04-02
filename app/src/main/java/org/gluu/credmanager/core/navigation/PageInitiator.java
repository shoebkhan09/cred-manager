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
 * @author jgomer
 */
public class PageInitiator extends CommonInitiator implements Initiator {

    @Override
    public void doInit(Page page, Map<String, Object> map) throws Exception {
        init(page);
        //TODO: security check?
    }

}
