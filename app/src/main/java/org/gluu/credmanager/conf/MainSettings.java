/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.conf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * @author jgomer
 */
//@Named
public class MainSettings {

    @JsonIgnore
    private File sourceFile;

    @JsonIgnore
    private ObjectMapper mapper;

    @JsonProperty("enable_pass_reset")
    private boolean enablePassReset;

    @JsonProperty("plugins_path")
    private String pluginsPath;

    @JsonProperty("branding_path")
    private String brandingPath;

    @JsonProperty("log_level")
    private String logLevel;

    @JsonProperty("min_creds_2FA")
    private int minCredsFor2FA;

    @JsonProperty("ldap_settings")
    private LdapSettings ldapSettings;

    //Maintained for backwards compatibility reasons
    @JsonProperty("enabled_methods")
    private List<String> enabledMethods;

    @JsonProperty("acr_plugin_mapping")
    private Map<String, String> acrPluginMap;

    @JsonProperty("extra_css")
    private String extraCssSnippet;

    @JsonProperty("u2f_settings")
    private U2fSettings u2fSettings;

    @JsonProperty("oxd_config")
    private OxdSettings oxdSettings;

    MainSettings() {
        mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
    }

    File getSourceFile() {
        return sourceFile;
    }

    void setSourceFile(File sourceFile) {
        this.sourceFile = sourceFile;
    }

    public void save() throws Exception {
        //update file to disk
        mapper.writeValue(sourceFile, this);
    }

    public boolean isEnablePassReset() {
        return enablePassReset;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getLogLevel() {
        return logLevel;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getPluginsPath() {
        return pluginsPath;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getBrandingPath() {
        return brandingPath;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<String> getEnabledMethods() {
        return enabledMethods;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> getAcrPluginMap() {
        return acrPluginMap;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getExtraCssSnippet() {
        return extraCssSnippet;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public U2fSettings getU2fSettings() {
        return u2fSettings;
    }

    public OxdSettings getOxdSettings() {
        return oxdSettings;
    }

    public Integer getMinCredsFor2FA() {
        return minCredsFor2FA;
    }

    public LdapSettings getLdapSettings() {
        return ldapSettings;
    }

    public void setPluginsPath(String pluginsPath) {
        this.pluginsPath = pluginsPath;
    }

    public void setEnablePassReset(boolean enablePassReset) {
        this.enablePassReset = enablePassReset;
    }

    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    public void setLdapSettings(LdapSettings ldapSettings) {
        this.ldapSettings = ldapSettings;
    }

    public void setBrandingPath(String brandingPath) {
        this.brandingPath = brandingPath;
    }

    public void setMinCredsFor2FA(int minCredsFor2FA) {
        this.minCredsFor2FA = minCredsFor2FA;
    }

    public void setEnabledMethods(List<String> enabledMethods) {
        this.enabledMethods = enabledMethods;
    }

    public void setAcrPluginMap(Map<String, String> acrPluginMap) {
        this.acrPluginMap = acrPluginMap;
    }

    public void setExtraCssSnippet(String extraCssSnippet) {
        this.extraCssSnippet = extraCssSnippet;
    }

    public void setU2fSettings(U2fSettings u2fSettings) {
        this.u2fSettings = u2fSettings;
    }

    public void setOxdSettings(OxdSettings oxdSettings) {
        this.oxdSettings = oxdSettings;
    }

}
