/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;


import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.core.pojo.User;

import javax.annotation.PostConstruct;
import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.Serializable;
import java.time.ZoneOffset;

/**
 * @author jgomer
 */
@Named
@SessionScoped
public class SessionContext implements Serializable {

    @Inject
    private MainSettings settings;

    private User user;

    private String custdir;

    private Boolean u2fSupported;

    private ZoneOffset zoneOffset;

    @PostConstruct
    private void inited() {
        //custdir is fixed during the whole session no matter if admin specified a different thing
        custdir = settings.getBrandingPath() == null ? "" : "/custom";
    }

    public ZoneOffset getZoneOffset() {
        return zoneOffset;
    }

    public String getCustdir() {
        return custdir;
    }

    public Boolean getU2fSupported() {
        return u2fSupported;
    }

    public User getUser() {
        return user;
    }

    public void setCustdir(String custdir) {
        this.custdir = custdir;
    }

    public void setU2fSupported(Boolean u2fSupported) {
        this.u2fSupported = u2fSupported;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public void setZoneOffset(ZoneOffset zoneOffset) {
        this.zoneOffset = zoneOffset;
    }

}
