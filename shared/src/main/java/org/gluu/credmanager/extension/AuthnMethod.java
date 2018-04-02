/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.extension;

import org.pf4j.ExtensionPoint;

/**
 * @author jgomer
 */
public interface AuthnMethod extends ExtensionPoint, UserCredentialsInfo {

    String getAcr();
    String getName();
    String getUINameKey();
    String getPanelTitleKey();
    String getPanelTextKey();
    String getPanelButtonKey();
    String getPanelBottomTextKey();
    String getPageUrl();

}
