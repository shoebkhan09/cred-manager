/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.services;

import org.gluu.credmanager.conf.AppConfiguration;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-08.
 * A conglomerate of beans that expose all important business logic to the application. Users of this class are mainly
 * view-model classes of the UI. See https://github.com/GluuFederation/cred-manager/blob/master/app/developer-notes.md#objects-stored-in-user-session
 * This bean has only getter methods.
 * Every member may have a LdapService bean injected if it requires reading or writing to LDAP, so UI is "distant" to
 * storage
 */
@SessionScoped
public class ServiceMashup implements Serializable {

    @Inject
    private AppConfiguration appConfiguration;

    @Inject
    private UserService userService;

    @Inject
    private OxdService oxdService;

    @Inject
    private OTPService otpService;

    @Inject
    private SmsService smsService;

    @Inject
    private U2fService u2fService;

    @Inject
    private SGService sgService;

    @Inject
    private AdminService adminService;

    public AppConfiguration getAppConfig() {
        return appConfiguration;
    }

    public UserService getUserService() {
        return userService;
    }

    public OxdService getOxdService() {
        return oxdService;
    }

    public OTPService getOtpService() {
        return otpService;
    }

    public SmsService getSmsService() {
        return smsService;
    }

    public U2fService getU2fService() {
        return u2fService;
    }

    public SGService getSgService() {
        return sgService;
    }

    public AdminService getAdminService() {
        return adminService;
    }

}
