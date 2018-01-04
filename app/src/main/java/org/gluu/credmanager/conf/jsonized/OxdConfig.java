/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Created by jgomer on 2017-07-07.
 */
public class OxdConfig{

    private String host;
    private int port;
    private String redirectUri;
    private String postLogoutUri;
    private boolean useHttpsExtension;
    private String opHost;
    private List<String> acrValues;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    public boolean isUseHttpsExtension() {
        return useHttpsExtension;
    }

    @JsonIgnore
    public String getPostLogoutUri() {
        return postLogoutUri;
    }

    @JsonIgnore
    public String getOpHost() {
        return opHost;
    }

    @JsonIgnore
    public List<String> getAcrValues() {
        return acrValues;
    }

    public void setOpHost(String opHost) {
        this.opHost = opHost;
    }

    @JsonProperty("authz_redirect_uri")
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    @JsonProperty("use_https_extension")
    public void setUseHttpsExtension(boolean useHttpsExtension) {
        this.useHttpsExtension = useHttpsExtension;
    }

    public void setPostLogoutUri(String postLogoutUri) {
        this.postLogoutUri = postLogoutUri;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public void setAcrValues(List<String> acrValues) {
        this.acrValues = acrValues;
    }

}