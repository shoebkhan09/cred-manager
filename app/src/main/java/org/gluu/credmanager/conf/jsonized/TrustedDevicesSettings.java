/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jgomer on 2018-04-23.
 */
public class TrustedDevicesSettings {

    @JsonProperty("location_exp_days")
    private Integer locationExpirationDays;

    @JsonProperty("device_exp_days")
    private Integer deviceExpirationDays;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Integer getLocationExpirationDays() {
        return locationExpirationDays;
    }

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Integer getDeviceExpirationDays() {
        return deviceExpirationDays;
    }

    public void setLocationExpirationDays(Integer locationExpirationDays) {
        this.locationExpirationDays = locationExpirationDays;
    }

    public void setDeviceExpirationDays(Integer deviceExpirationDays) {
        this.deviceExpirationDays = deviceExpirationDays;
    }

}
