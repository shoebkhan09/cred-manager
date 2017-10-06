package org.gluu.credmanager.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.services.ldap.LdapService;
import org.xdi.ldap.model.SimpleUser;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
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

}
