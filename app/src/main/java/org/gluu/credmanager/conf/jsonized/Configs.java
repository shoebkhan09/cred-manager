/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.gluu.credmanager.conf.OTPConfig;
import org.gluu.credmanager.conf.SGConfig;
import org.gluu.credmanager.conf.TwilioConfig;
import org.gluu.credmanager.conf.sndfactor.EnforcementPolicy;

import java.util.List;

/**
 * Created by jgomer on 2017-07-06.
 */
public class Configs {

    @JsonProperty("enable_pass_reset")
    private boolean enablePassReset;

    @JsonProperty("branding_path")
    private String brandingPath;

    @JsonProperty("oxd_config")
    private OxdConfig oxdConfig;

    @JsonProperty("ldap_settings")
    private LdapSettings ldapSettings;

    @JsonProperty("u2f_settings")
    private U2fSettings u2fSettings;

    @JsonProperty("enabled_methods")
    private List<String> enabledMethods;

    @JsonProperty("gluu_version")
    private String gluuVersion;

    @JsonProperty("log_level")
    private String logLevel;

    @JsonProperty("min_creds_2FA")
    private Integer minCredsFor2FA;

    @JsonProperty("policy_2fa")
    private List<EnforcementPolicy> enforcement2FA;

    @JsonProperty("trusted_dev_settings")
    private TrustedDevicesSettings trustedDevicesSettings;

    @JsonIgnore
    private TwilioConfig twilioConfig;

    @JsonIgnore
    private OTPConfig otpConfig;

    @JsonIgnore
    private SGConfig sgConfig;

    public LdapSettings getLdapSettings() {
        return ldapSettings;
    }

    public boolean isEnablePassReset() {
        return enablePassReset;
    }

    public OxdConfig getOxdConfig() {
        return oxdConfig;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getEnabledMethods() {
        return enabledMethods;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getLogLevel() {
        return logLevel;
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public TrustedDevicesSettings getTrustedDevicesSettings() {
        return trustedDevicesSettings;
    }

    public Integer getMinCredsFor2FA() {
        return minCredsFor2FA;
    }

    public SGConfig getSgConfig() {
        return sgConfig;
    }
    public OTPConfig getOtpConfig() {
        return otpConfig;
    }
    public TwilioConfig getTwilioConfig() {
        return twilioConfig;
    }

    public List<EnforcementPolicy> getEnforcement2FA() {
        return enforcement2FA;
    }

    public void setTrustedDevicesSettings(TrustedDevicesSettings trustedDevicesSettings) {
        this.trustedDevicesSettings = trustedDevicesSettings;
    }

    public void setEnforcement2FA(List<EnforcementPolicy> enforcement2FA) {
        this.enforcement2FA = enforcement2FA;
    }

    public void setMinCredsFor2FA(Integer minCredsFor2FA) {
        this.minCredsFor2FA = minCredsFor2FA;
    }

    public void setBrandingPath(String brandingPath) {
        this.brandingPath = brandingPath;
    }

    public void setU2fSettings(U2fSettings u2fSettings) {
        this.u2fSettings = u2fSettings;
    }

    public void setLdapSettings(LdapSettings ldapSettings) {
        this.ldapSettings = ldapSettings;
    }

    public void setOxdConfig(OxdConfig oxdConfig) {
        this.oxdConfig = oxdConfig;
    }

    public void setEnablePassReset(boolean enablePassReset) {
        this.enablePassReset = enablePassReset;
    }

    public void setEnabledMethods(List<String> enabledMethods) {
        this.enabledMethods = enabledMethods;
    }

    public void setGluuVersion(String gluuVersion) {
        this.gluuVersion = gluuVersion;
    }

    public void setTwilioConfig(TwilioConfig twilioConfig) {
        this.twilioConfig = twilioConfig;
    }

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