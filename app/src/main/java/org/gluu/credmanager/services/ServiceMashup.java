package org.gluu.credmanager.services;

import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.services.oxd.OxdService;

import javax.enterprise.context.SessionScoped;
import javax.inject.Inject;
import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-08.
 */
@SessionScoped
public class ServiceMashup implements Serializable {

    @Inject
    private OxdService oxdService;

    @Inject
    private UserService userService;

    @Inject
    private AppConfiguration appConfiguration;

    public AppConfiguration getAppConfig() {
        return appConfiguration;
    }

    public UserService getUserService() {
        return userService;
    }

    public OxdService getOxdService() {
        return oxdService;
    }

}
