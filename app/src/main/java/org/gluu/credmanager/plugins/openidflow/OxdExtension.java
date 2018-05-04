/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.plugins.openidflow;

import org.gluu.credmanager.extension.OpenIdFlow;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.plugins.BaseSystemExtension;
import org.pf4j.Extension;

import java.util.Map;

/**
 * @author jgomer
 */
@Extension
public class OxdExtension extends BaseSystemExtension implements OpenIdFlow {

    private OxdService oxdService;

    private Boolean inited;

    public OxdExtension() {
        oxdService = Utils.managedBean(OxdService.class);
        inited = null;
    }

    public boolean isInited() {
        if (inited == null) {
            inited = oxdService.initialize();
        }
        return inited;
    }

    public String getName() {
        return "oxd";
    }

    public String getAuthzUrl(Map<String, String> requestParams) {
        return null;
    }

    public Map<String, String> getTokens(Map<String, String> requestParams) {
        return null;
    }

    public Map<String, String> getUserInfo(String accessToken) {
        return null;
    }

    public String getLogoutUrl(String idTokenHint) {
        return null;
    }

}
