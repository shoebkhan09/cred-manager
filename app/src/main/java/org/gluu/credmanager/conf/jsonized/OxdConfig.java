package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * Created by jgomer on 2017-07-07.
 */
public class OxdConfig{

    private String host;
    private int port;
    private String oxdId;
    private String redirectUri;
    private String postLogoutUri;
    private String clientName;
    private Set<String> acrValues;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOxdId() {
        return oxdId;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    @JsonIgnore
    public String getPostLogoutUri() {
        return postLogoutUri;
    }

    @JsonIgnore
    public String getClientName() {
        return clientName;
    }

    @JsonIgnore
    public Set<String> getAcrValues() {
        return acrValues;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

    @JsonProperty("oxd-id")
    public void setOxdId(String oxdId) {
        this.oxdId = oxdId;
    }

    @JsonProperty("authz_redirect_uri")
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }

    public void setPostLogoutUri(String postLogoutUri) {
        this.postLogoutUri = postLogoutUri;
    }

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public void setAcrValues(Set<String> acrValues) {
        this.acrValues = acrValues;
    }

}