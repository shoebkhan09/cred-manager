/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.conf;

/**
 * Created by jgomer on 2017-12-04.
 */
public class ComputedOxdSettings {

    private String oxdId;
    private String clientId;
    private String clientSecret;
    private String clientName;

    public ComputedOxdSettings(String clientName, String oxdId, String clientId, String clientSecret){

        this.clientName = clientName;
        this.oxdId = oxdId;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
    }

    public String getOxdId() {
        return oxdId;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getClientName() {
        return clientName;
    }

}
