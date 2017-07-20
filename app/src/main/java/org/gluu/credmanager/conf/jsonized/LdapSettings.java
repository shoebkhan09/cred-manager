package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jgomer on 2017-07-07.
 */
public class LdapSettings {

    private String saltLocation;
    private String oxLdapLocation;
    private String applianceInum;
    private String orgInum;

    public String getApplianceInum() {
        return applianceInum;
    }

    public String getOrgInum() {
        return orgInum;
    }

    public String getSaltLocation() {
        return saltLocation;
    }

    public String getOxLdapLocation() {
        return oxLdapLocation;
    }

    @JsonProperty("salt")
    public void setSaltLocation(String saltLocation) {
        this.saltLocation = saltLocation;
    }

    @JsonProperty("ox-ldap_location")
    public void setOxLdapLocation(String oxLdapLocation) {
        this.oxLdapLocation = oxLdapLocation;
    }

    public void setApplianceInum(String applianceInum) {
        this.applianceInum = applianceInum;
    }

    public void setOrgInum(String orgInum) {
        this.orgInum = orgInum;
    }

}