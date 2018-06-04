/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.conf.sndfactor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Created by jgomer on 2018-04-18.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TrustedDevice {

    private Browser browser;
    private OperatingSystem os;
    private List<TrustedOrigin> origins;

    public Browser getBrowser() {
        return browser;
    }

    public void setBrowser(Browser browser) {
        this.browser = browser;
    }

    public OperatingSystem getOs() {
        return os;
    }

    public void setOs(OperatingSystem os) {
        this.os = os;
    }

    public List<TrustedOrigin> getOrigins() {
        return origins;
    }

    public void setOrigins(List<TrustedOrigin> origins) {
        this.origins = origins;
    }

    public void sortOriginsDescending() {

        if (origins != null) {
            origins.sort((o1, o2) -> {

                if (o1 == null && o2 == null) {
                    return 0;
                }
                if (o1 == null) {
                    return 1;
                }
                if (o2 == null) {
                    return -1;
                }

                Long origin1 = Long.valueOf(o1.getTimestamp());
                Long origin2 = Long.valueOf(o2.getTimestamp());

                return origin2.compareTo(origin1);
            });
        }

    }

}
