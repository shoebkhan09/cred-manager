/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.extension;

import org.pf4j.ExtensionPoint;

import java.util.Map;

/**
 * Defines the steps of the OpenId Connect Authorization Code Flow as well as retrieving user info (claims) and logout url
 * @author jgomer
 */
public interface OpenIdFlow extends ExtensionPoint {

    String getName();
    String getAuthzUrl(Map<String, String> requestParams);
    Map<String, String> getTokens(Map<String, String> requestParams);
    Map<String, String> getUserInfo(String accessToken);
    String getLogoutUrl(String idTokenHint);

}
