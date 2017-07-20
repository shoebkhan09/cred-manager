package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jgomer on 2017-07-06.
 */

//TODO: implement the twilio part

@JsonIgnoreProperties({ "twilio_settings" })
public class ConfigFile {

    private boolean enablePassReset;
    private OxdConfig oxdConfig;
    private LdapSettings ldapSettings;
    private String[] enabledMethods;
    private String gluuVersion;

    public LdapSettings getLdapSettings() {
        return ldapSettings;
    }

    public boolean isEnablePassReset() {
        return enablePassReset;
    }

    public OxdConfig getOxdConfig() {
        return oxdConfig;
    }

    public String[] getEnabledMethods() {
        return enabledMethods;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getGluuVersion() {
        return gluuVersion;
    }

    @JsonProperty("ldap_settings")
    public void setLdapSettings(LdapSettings ldapSettings) {
        this.ldapSettings = ldapSettings;
    }

    @JsonProperty("oxd_config")
    public void setOxdConfig(OxdConfig oxdConfig) {
        this.oxdConfig = oxdConfig;
    }

    @JsonProperty("enable_pass_reset")
    public void setEnablePassReset(boolean enablePassReset) {
        this.enablePassReset = enablePassReset;
    }

    @JsonProperty("enabled_methods")
    public void setEnabledMethods(String[] enabledMethods) {
        this.enabledMethods = enabledMethods;
    }

    @JsonProperty("gluu_version")
    public void setGluuVersion(String gluuVersion) {
        this.gluuVersion = gluuVersion;
    }

}