package org.gluu.credmanager.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.ldap.pojo.GluuPerson;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by jgomer on 2017-07-16.
 */
@ApplicationScoped
public class UserService {

    public static final String[] requiredOpenIdScopes =
            new String[]{"openid","profile","user_name","email","mobile_phone","phone","clientinfo"};

    @Inject
    LdapService ldapService;

    private Logger logger = LogManager.getLogger(getClass());

    public User createUserFromClaims(Map<String, List<String>> claims){

        logger.debug("User claims {}", claims.toString());

        User u = new User();
        u.setUserName(getClaim(claims,"user_name"));
        u.setGivenName(getClaim(claims,"given_name"));
        u.setEmail(getClaim(claims,"email"));
        u.setPhone(getClaim(claims, "phone_number_verified"));
        u.setMobilePhone(getClaim(claims, "phone_mobile_number"));

        String inum=getClaim(claims, "inum");
        if (inum!=null)
            u.setRdn("inum=" + inum);

        return u;
    }

    private String getClaim(Map<String, List<String>> claims, String claimName){
        List<String> values=claims.get(claimName);
        return (values==null || values.size()==0) ? null : values.get(0);
    }

    public CredentialType getPreferredMethod(User user) throws Exception{
        GluuPerson person=ldapService.getGluuPerson(user.getRdn());
        return CredentialType.getType(person.getPreferredAuthMethod());
    }

    //TODO: implement this
    public Set<CredentialType> getPersonalMethods(User user) throws Exception{
        return null;
    }

}