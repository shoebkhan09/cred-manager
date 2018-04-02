/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.conf;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author jgomer
 */
public class LdapSettings {

    @JsonProperty("salt")
    private String saltLocation;

    @JsonProperty("ox-ldap_location")
    private String oxLdapLocation;

    public String getSaltLocation() {
        return saltLocation;
    }

    public void setSaltLocation(String saltLocation) {
        this.saltLocation = saltLocation;
    }

    public String getOxLdapLocation() {
        return oxLdapLocation;
    }

    public void setOxLdapLocation(String oxLdapLocation) {
        this.oxLdapLocation = oxLdapLocation;
    }

}
