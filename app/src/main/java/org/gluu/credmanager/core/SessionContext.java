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

    @Inject
    private ZKService zkService;

    private User user;

    private String custdir;

    private String cssPath;

    private Boolean onMobileBrowser;

    private boolean onMobile;

    private Boolean u2fSupported;

    private ZoneOffset zoneOffset;

    private String logoDataUri;

    private String faviconDataUri;

    @PostConstruct
    private void inited() {

        //these 3 variables are fixed during the whole session no matter if a different thing was specified later via admin UI
        custdir = settings.getBrandingPath() == null ? "" : ZKService.DEFAULT_CUSTOM_PATH;
        logoDataUri = zkService.getLogoDataUri();
        faviconDataUri = zkService.getFaviconDataUri();

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

    public Boolean getOnMobileBrowser() {
        return onMobileBrowser;
    }

    public boolean getOnMobile() {
        return onMobile;
    }

    public String getCssPath() {
        return cssPath;
    }

    public String getLogoDataUri() {
        return logoDataUri;
    }

    public String getFaviconDataUri() {
        return faviconDataUri;
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

    public void setOnMobileBrowser(Boolean onMobileBrowser) {
        this.onMobileBrowser = onMobileBrowser;
    }

    public void setOnMobile(boolean onMobile) {
        this.onMobile = onMobile;
    }

    public void setCssPath(String cssPath) {
        this.cssPath = cssPath;
    }

}
