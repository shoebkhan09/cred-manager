package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jgomer on 2017-07-06.
 */

public class ConfigFile {

    private boolean enablePassReset;
    private OxdConfig oxdConfig;
    private LdapSettings ldapSettings;
    private TwilioConfig twilioConfig;
    private String[] enabledMethods;
    private String gluuVersion;
    private String u2fRelativeMetadataUri;

    public String getU2fRelativeMetadataUri() {
        return u2fRelativeMetadataUri;
    }

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
    public TwilioConfig getTwilioConfig() {
        return twilioConfig;
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

    @JsonProperty("twilio_settings")
    public void setTwilioConfig(TwilioConfig twilioConfig) {
        this.twilioConfig = twilioConfig;
    }

    @JsonProperty("u2f_relative_uri")
    public void setU2fRelativeMetadataUri(String u2fRelativeMetadataUri) {
        this.u2fRelativeMetadataUri = u2fRelativeMetadataUri;
    }

}