/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.conf;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author jgomer
 */
public class U2fSettings {

    private String appId;
    private String relativeMetadataUri;
    private String endpointUrl;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getRelativeMetadataUri() {
        return relativeMetadataUri;
    }

    @JsonProperty("u2f_relative_uri")
    public void setRelativeMetadataUri(String relativeMetadataUri) {
        this.relativeMetadataUri = relativeMetadataUri;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String getAppId() {
        return appId;
    }

    @JsonProperty("app_id")
    public void setAppId(String appId) {
        this.appId = appId;
    }

    @JsonIgnore
    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(String endpointUrl) {
        this.endpointUrl = endpointUrl;
    }

}
