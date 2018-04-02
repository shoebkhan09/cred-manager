/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core;

import org.gluu.credmanager.core.ldap.gluuPerson;
import org.gluu.credmanager.core.pojo.User;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.LdapService;
import org.slf4j.Logger;
import org.zkoss.util.Pair;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * An app. scoped bean that encapsulates logic related to users manipulation (CRUD) at memory level (no LDAP storage)
 * @author jgomer
 */
@Named
@ApplicationScoped
public class UserService {

    @Inject
    private Logger logger;

    @Inject
    private LdapService ldapService;

    @Inject
    private ExtensionsManager extManager;

    @Inject
    private ConfigurationHandler confHandler;

    public boolean passwordMatch(String userName, String password) {

        boolean match = false;
        try {
            match = ldapService.authenticate(userName, password);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return match;

    }

    public boolean changePassword(String userId, String newPassword) {

        boolean success = false;
        try {
            if (Utils.isNotEmpty(newPassword)) {
                gluuPerson person = ldapService.get(gluuPerson.class, ldapService.getPersonDn(userId));
                person.setPassword(newPassword);
                success = ldapService.modify(person, gluuPerson.class);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public List<AuthnMethod> getLiveAuthnMethods() {
        //The following map contains entries associated to active acr methods in oxauth
        Map<String, Integer> authnMethodLevels = confHandler.getAcrLevelMapping();
        return extManager.getAuthnMethodExts().stream().filter(aMethod -> authnMethodLevels.get(aMethod.getAcr()) != null)
                .sorted(Comparator.comparing(aMethod -> -authnMethodLevels.get(aMethod.getAcr()))).collect(Collectors.toList());
    }

    public List<Pair<AuthnMethod, Integer>> getUserMethodsCount(String userId, Set<String> retainMethods) {
        return extManager.getAuthnMethodExts().stream().filter(aMethod -> retainMethods.contains(aMethod.getAcr()))
                .map(aMethod -> new Pair<>(aMethod, aMethod.getTotalUserCreds(userId, true)))
                .filter(pair -> pair.getY() > 0).collect(Collectors.toList());
    }

    public boolean setPreferredMethod(User user, String method) {

        boolean success = false;
        try {
            gluuPerson person = ldapService.get(gluuPerson.class, ldapService.getPersonDn(user.getId()));
            person.setPreferredMethod(method);
            success = ldapService.modify(person, gluuPerson.class);
            if (success) {
                user.setPreferredMethod(method);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public void setupRequisites(String userId) {
        //TODO: implement
        //prepareFidoBranch(rdn);
        //cleanRandEnrollmentCode(user);
    }

}
