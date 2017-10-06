package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.gluu.credmanager.conf.OTPConfig;
import org.gluu.credmanager.conf.SGConfig;
import org.gluu.credmanager.conf.TwilioConfig;

/**
 * Created by jgomer on 2017-07-06.
 */

public class Configs {

    private boolean enablePassReset;
    private String brandingPath;
    private OxdConfig oxdConfig;
    private LdapSettings ldapSettings;
    private TwilioConfig twilioConfig;
    private OTPConfig otpConfig;
    private SGConfig sgConfig;
    private U2fSettings u2fSettings;
    private String[] enabledMethods;
    private String gluuVersion;
    private String logLevel;

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
    public String getLogLevel() {
        return logLevel;
    }

    @JsonIgnore
    public SGConfig getSgConfig() {
        return sgConfig;
    }

    @JsonIgnore
    public OTPConfig getOtpConfig() {
        return otpConfig;
    }

    @JsonIgnore
    public TwilioConfig getTwilioConfig() {
        return twilioConfig;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getGluuVersion() {
        return gluuVersion;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public U2fSettings getU2fSettings() {
        return u2fSettings;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getBrandingPath() {
        return brandingPath;
    }

    @JsonProperty("branding_path")
    public void setBrandingPath(String brandingPath) {
        this.brandingPath = brandingPath;
    }

    @JsonProperty("u2f_settings")
    public void setU2fSettings(U2fSettings u2fSettings) {
        this.u2fSettings = u2fSettings;
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

    @JsonProperty("log_level")
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setOtpConfig(OTPConfig otpConfig) {
        this.otpConfig = otpConfig;
    }

    public void setSgConfig(SGConfig sgConfig) {
        this.sgConfig = sgConfig;
    }

}