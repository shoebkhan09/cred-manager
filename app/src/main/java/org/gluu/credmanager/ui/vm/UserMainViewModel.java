package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.util.Pair;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Created by jgomer on 2017-07-08.
 */
public class UserMainViewModel {

    private Logger logger = LogManager.getLogger(getClass());
    private ServiceMashup services;
    private Session se;

    private boolean secondFactorAllowed;
    private Map<String, Boolean> credentialsVisibility;

    private User user;

    @Init
    public void init() throws Exception{
        se= Sessions.getCurrent();
        services=WebUtils.getServices(se);

        secondFactorAllowed=services.getAppConfig().getEnabledMethods().size()>0;
        user=WebUtils.getUser(se);

        //Update current user with complementary attributes

        //preferred method:
        user.setPreference(services.getUserService().getPreferredMethod(user));

        //credentials he has added so far:
        List<RegisteredCredential> userCreds=services.getUserService().getPersonalMethods(user);
logger.debug("user creds {}", userCreds.stream().map(RegisteredCredential::getDn).collect(Collectors.toList()));
        if (userCreds==null) {
            //TODO: does it mean an error?
            userCreds=new ArrayList<>();
        }
        user.setCredentials(userCreds);

        credentialsVisibility=new HashMap<>();
        Set<CredentialType> systemLevelCreds=services.getAppConfig().getEnabledMethods();
        for (CredentialType cred : CredentialType.values()){
            credentialsVisibility.put(cred.name(), systemLevelCreds.contains(cred));
        }

    }

    @Command
    public void logoutFromAuthzServer(){
        try {
            Executions.sendRedirect(services.getOxdService().getLogoutUrl());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }

    }

    public User getUser() {
        return user;
    }

    public boolean isSecondFactorAllowed() {
        return secondFactorAllowed;
    }

    public Map<String, Boolean> getCredentialsVisibility() {
        return credentialsVisibility;
    }

}