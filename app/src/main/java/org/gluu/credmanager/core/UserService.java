/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.ldap.sdk.Filter;
import org.gluu.credmanager.conf.sndfactor.EnforcementPolicy;
import org.gluu.credmanager.conf.sndfactor.TrustedDevice;
import org.gluu.credmanager.conf.sndfactor.TrustedDeviceComparator;
import org.gluu.credmanager.core.ldap.Person;
import org.gluu.credmanager.core.ldap.PersonPreferences;
import org.gluu.credmanager.core.pojo.User;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.LdapService;
import org.slf4j.Logger;
import org.xdi.util.security.StringEncrypter;
import org.zkoss.util.Pair;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An app. scoped bean that encapsulates logic related to users manipulation (CRUD) at memory level (no LDAP storage)
 * @author jgomer
 */
@Named
@ApplicationScoped
public class UserService {

    /*
    The list of OpenId scopes required to be able to inspect the claims needed. See attributes of User class
     */
    public static final String[] OPEN_ID_SCOPES = new String[]{ "openid", "profile", "user_name", "clientinfo" };

    private static final String PREFERRED_METHOD_ATTR = "oxPreferredMethod";

    @Inject
    private Logger logger;

    @Inject
    private LdapService ldapService;

    @Inject
    private ExtensionsManager extManager;

    @Inject
    private ConfigurationHandler confHandler;

    private ObjectMapper mapper=new ObjectMapper();

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
                PersonPreferences person = ldapService.get(PersonPreferences.class, ldapService.getPersonDn(userId));
                person.setPassword(newPassword);
                success = ldapService.modify(person, PersonPreferences.class);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public List<AuthnMethod> getLiveAuthnMethods() {
        Map<String, Integer> authnMethodLevels = confHandler.getAcrLevelMapping();
        Set<String> mappedAcrs = confHandler.getSettings().getAcrPluginMap().keySet();
        return extManager.getAuthnMethodExts().stream().filter(aMethod -> mappedAcrs.contains(aMethod.getAcr()))
                .sorted(Comparator.comparing(aMethod -> -authnMethodLevels.get(aMethod.getAcr()))).collect(Collectors.toList());
    }

    public List<Pair<AuthnMethod, Integer>> getUserMethodsCount(String userId, Set<String> retainMethods) {
        return extManager.getAuthnMethodExts().stream().filter(aMethod -> retainMethods.contains(aMethod.getAcr()))
                .map(aMethod -> new Pair<>(aMethod, aMethod.getTotalUserCreds(userId, true)))
                .filter(pair -> pair.getY() > 0).collect(Collectors.toList());
    }

    public boolean setPreferredMethod(User user, String method) {

        boolean success = setPreferredMethod(ldapService.getPersonDn(user.getId()), method);
        if (success) {
            user.setPreferredMethod(method);
        }
        return success;

    }

    /**
     * Resets the preferred method of authentication for the users referenced by LDAP dn
     * @param userInums A List containing user DNs
     * @return The number of modified entries in LDAP
     */
    public int resetPreference(List<String> userInums) {

        int modified = 0;
        try {
            for (String inum : userInums) {
                if (setPreferredMethod(ldapService.getPersonDn(inum), null)) {
                    modified++;
                    logger.info("Reset preferred method for user '{}'", inum);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return modified;

    }

    public void setupRequisites(String userId) {
        //TODO: implement
        //prepareFidoBranch(rdn);
        //cleanRandEnrollmentCode(user);
    }
    /**
     * Builds a list of users whose username, first or last name matches the pattern passed, and at the same time have a
     * preferred authentication method other than password
     * @param searchString Pattern for search
     * @return A collection of SimpleUser instances. Null if an error occurred to compute the list
     */
    public List<Person> searchUsers(String searchString) {

        Stream<Filter> stream = Stream.of("uid", "givenName", "sn")
                .map(attr -> Filter.createSubstringFilter(attr, null, new String[]{ searchString }, null));

        Filter filter = Filter.createANDFilter(
                Filter.createORFilter(stream.collect(Collectors.toList())),
                Filter.createPresenceFilter(PREFERRED_METHOD_ATTR)
        );
        return ldapService.find(Person.class, ldapService.getPeopleDn(), filter.toString());

    }

    private boolean setPreferredMethod(String dn, String method) {

        boolean success = false;
        try {
            PersonPreferences person = ldapService.get(PersonPreferences.class, dn);
            person.setPreferredMethod(method);
            success = ldapService.modify(person, PersonPreferences.class);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    /**
     * Determines if there are no users with this type of method as preferred in LDAP
     * @param acr
     * @return False if any user has type as his preferred. True otherwise
     */
    public boolean zeroPreferences(String acr){
        PersonPreferences ppfs = new PersonPreferences();
        ppfs.setPreferredMethod(acr);
        return ldapService.find(ppfs, PersonPreferences.class, ldapService.getPeopleDn()).size() == 0;
    }

    public Pair<Set<String>, List<TrustedDevice>> get2FAPolicyData(String userId) {

        Set<String> list = new HashSet<>();
        List<TrustedDevice> trustedDevices = new ArrayList<>();
        try {
            PersonPreferences person = ldapService.get(PersonPreferences.class, ldapService.getPersonDn(userId));
            String policy = person.getStrongAuthPolicy();

            if (Utils.isNotEmpty(policy)) {
                Stream.of(policy.split(",\\s*")).forEach(str -> {
                    try {
                        list.add(EnforcementPolicy.valueOf(str.toUpperCase()).toString());
                    } catch (Exception e) {
                        logger.error("The policy '{}' is not recognized", str);
                    }
                });
            }

            String trustedDevicesInfo = ldapService.getEncryptedString(person.getTrustedDevicesInfo());
            if (Utils.isNotEmpty(trustedDevicesInfo)) {
                trustedDevices = mapper.readValue(trustedDevicesInfo, new TypeReference<List<TrustedDevice>>() { });
                trustedDevices.forEach(TrustedDevice::sortOriginsDescending);

                TrustedDeviceComparator comparator = new TrustedDeviceComparator(true);
                trustedDevices.sort((first, second) -> comparator.compare(second, first));
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return new Pair<>(list, trustedDevices);

    }

    public boolean update2FAPolicies(String userId, Set<String> policies) {

        boolean updated = false;
        String str = policies.stream().map(String::toLowerCase).reduce("", (partial, next) -> partial + ", " + next);
        try {
            PersonPreferences person = ldapService.get(PersonPreferences.class, ldapService.getPersonDn(userId));
            person.setStrongAuthPolicy(str.substring(2));
            updated = ldapService.modify(person, PersonPreferences.class);
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return updated;

    }

    public boolean deleteTrustedDevice(String userId, List<TrustedDevice> devices, int index) {

        boolean updated = false;
        List<TrustedDevice> copyOfDevices = new ArrayList<>(devices);
        try {
            copyOfDevices.remove(index);
            String updatedJson = ldapService.getEncryptedString(mapper.writeValueAsString(copyOfDevices));

            PersonPreferences person = ldapService.get(PersonPreferences.class, ldapService.getPersonDn(userId));
            person.setTrustedDevices(updatedJson);
            if (ldapService.modify(person, PersonPreferences.class)) {
                devices.remove(index);
                updated = true;
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return updated;

    }

}
