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
    private boolean useHttpsExtension;
    private String clientId;
    private String clientSecret;

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getRedirectUri() {
        return redirectUri;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getOxdId() {
        return oxdId;
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

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getClientId() {
        return clientId;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getClientSecret() {
        return clientSecret;
    }

    public boolean isUseHttpsExtension() {
        return useHttpsExtension;
    }

    @JsonProperty("client_id")
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    @JsonProperty("client_secret")
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    @JsonProperty("oxd-id")
    public void setOxdId(String oxdId) {
        this.oxdId = oxdId;
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

    public void setClientName(String clientName) {
        this.clientName = clientName;
    }

    public void setAcrValues(Set<String> acrValues) {
        this.acrValues = acrValues;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void setPort(int port) {
        this.port = port;
    }

}