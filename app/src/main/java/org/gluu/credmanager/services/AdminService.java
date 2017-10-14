package org.gluu.credmanager.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.services.ldap.LdapService;
import org.xdi.ldap.model.SimpleUser;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * Created by jgomer on 2017-10-05.
 * An app. scoped bean that contains method that helps to achieve administrative tasks
 */
@ApplicationScoped
public class AdminService {

    private Logger logger = LogManager.getLogger(getClass());

    @Inject
    private LdapService ldapService;

    public void logAdminEvent(String description){
        logger.warn(Labels.getLabel("app.admin_event"), description);
    }

    /**
     * Builds a list of users whose username, first or last name matches the pattern passed, and at the same time have a
     * preferred authentication method other than password
     * @param str Pattern for search
     * @return A collection of SimpleUser instances. Null if an error occurred to compute the list
     */
    public List<SimpleUser> searchUsers(String str) {

        List<SimpleUser> list=null;
        try {
            list = ldapService.findUsersWithPreferred(str, new String[]{"uid", "givenName", "sn"}, SimpleUser.class);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return list;
    }

    /**
     * Resets the preferred method of authentication for the users referenced by LDAP dn
     * @param userDNs A List containing user DNs
     * @return The number of modified entries in LDAP
     */
    public int resetPreference(List<String> userDNs) {

        int modified=0;
        try{
            for (String dn : userDNs){
                ldapService.updatePreferredMethod(dn.substring(0, dn.indexOf(",")).trim(), null);
                modified++;
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return modified;

    }

    /**
     * Determines if there are no users with this type of method as preferred in LDAP
     * @param type A credential
     * @return False if any user has type as his preferred. True otherwise
     */
    public boolean zeroPreferences(CredentialType type){

        boolean zero;
        try {
            List<String> acrs=new ArrayList<>();
            acrs.add(type.getName());
            if (type.getAlternativeName()!=null)
                acrs.add(type.getAlternativeName());

            zero=ldapService.preferenceCount(acrs)==0;
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            zero=false;
        }
        return zero;

    }

}
