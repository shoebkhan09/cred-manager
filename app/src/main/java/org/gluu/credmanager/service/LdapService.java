/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPResult;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.SearchScope;
import com.unboundid.ldap.sdk.persist.LDAPPersister;
import com.unboundid.ldap.sdk.persist.PersistedObjects;
import org.gluu.credmanager.conf.LdapSettings;
import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.core.ldap.gluuOrganization;
import org.gluu.credmanager.core.ldap.gluuPersonMember;
import org.gluu.credmanager.core.ldap.oxAuthConfiguration;
import org.gluu.credmanager.core.ldap.oxCustomScript;
import org.gluu.credmanager.core.ldap.oxTrustConfiguration;
import org.gluu.credmanager.misc.Utils;
import org.gluu.persist.ldap.impl.LdapEntryManagerFactory;
import org.gluu.persist.ldap.operation.LdapOperationService;
import org.slf4j.Logger;
import org.xdi.util.properties.FileConfiguration;
import org.xdi.util.security.PropertiesDecrypter;
import org.xdi.util.security.StringEncrypter;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

/**
 * @author jgomer
 */
@Named
@ApplicationScoped
public class LdapService implements ILdapService {

    @Inject
    private Logger logger;

    @Inject
    private MainSettings settings;

    private boolean inService;

    private LdapOperationService ldapOperationService;

    private String orgInum;

    private JsonNode oxAuthConfDynamic;

    private JsonNode oxAuthConfStatic;

    private JsonNode oxTrustConfApplication;

    private JsonNode oxTrustConfCacheRefresh;

    private ObjectMapper mapper;

    public boolean isInService() {
        return inService;
    }

    public String getOIDCEndpoint() {
        return oxAuthConfDynamic.get("openIdConfigurationEndpoint").asText();
    }

    public String getIssuerUrl() {
        return oxAuthConfDynamic.get("issuer").asText();
    }

    @PostConstruct
    private void inited() {

        try {
            mapper = new ObjectMapper();
            LdapSettings ldapSettings = settings.getLdapSettings();

            Properties ldapProperties = new FileConfiguration(ldapSettings.getOxLdapLocation()).getProperties();
            String saltFile = ldapSettings.getSaltLocation();

            if (Utils.isNotEmpty(saltFile)) {
                String salt = new FileConfiguration(saltFile).getProperties().getProperty("encodeSalt");
                ldapProperties = PropertiesDecrypter.decryptProperties(StringEncrypter.instance(salt), ldapProperties);
            }
            ldapOperationService = new LdapEntryManagerFactory().createEntryManager(ldapProperties).getOperationService();

            mapper = new ObjectMapper();
            //Initialize important class members
            inService = loadApplianceSettings(ldapProperties);
            logger.info("LDAPService was{} initialized successfully", inService ? "" : " not");
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    public Map<String, String> getCustScriptConfigProperties(String displayName) {

        Map<String, String> properties = null;
        try {
            String dn = oxAuthConfStatic.get("baseDn").get("scripts").asText();
            oxCustomScript script = new oxCustomScript();
            script.setDisplayName(displayName);

            List<oxCustomScript> scripts = find(script, oxCustomScript.class, dn);
            if (scripts.size() > 0) {
                String[] props = scripts.get(0).getConfigurationProperties();

                properties = new HashMap<>();
                if (Utils.isNotEmpty(props)) {
                    for (String prop : props) {
                        try {
                            JsonNode node = mapper.readTree(prop);
                            String key = node.get("value1").asText();
                            String value = node.get("value2").asText();
                            properties.put(key, value);
                        } catch (Exception e) {
                            logger.error("Error reading a custom script configuration property ({})", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return properties;

    }

    public String getPersonDn(String id) {
        return String.format("inum=%s,%s", id, getPeopleDn());
    }

    public String getPeopleDn() {
        return oxAuthConfStatic.get("baseDn").get("people").asText();
    }

    public String getGroupsDn() {
        return oxAuthConfStatic.get("baseDn").get("groups").asText();
    }

    public String getClientsDn() {
        return oxAuthConfStatic.get("baseDn").get("clients").asText();
    }

    public String getScopesDn() {
        return oxAuthConfStatic.get("baseDn").get("scopes").asText();
    }

    public String getOrganizationInum() {
        return oxAuthConfDynamic.get("organizationInum").asText();
    }

    public gluuOrganization getOrganization() {
        return get(gluuOrganization.class, String.format("o=%s,o=gluu", getOrganizationInum()));
    }

    public boolean isAdmin(String userId) {
        gluuOrganization organization = getOrganization();
        DN[] dns = organization.getGluuManagerGroupDNs();

        gluuPersonMember personMember = get(gluuPersonMember.class, getPersonDn(userId));
        return personMember != null
                && personMember.getMemberOfDNsAsList().stream().anyMatch(m -> Stream.of(dns).anyMatch(dn -> m.equals(dn)));

    }

    public <T> T get(Class<T> clazz, String dn) {

        T object = null;
        try {
            LDAPPersister<T> persister = LDAPPersister.getInstance(clazz);
            object = persister.get(dn, ldapOperationService.getConnection());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return object;

    }

    public <T> List<T> find(T object, Class<T> clazz, String parentDn) {

        List<T> results = null;
        try {
            results = new ArrayList<>();
            LDAPPersister<T> persister = LDAPPersister.getInstance(clazz);
            PersistedObjects<T> objects = persister.search(object, ldapOperationService.getConnection(), parentDn, SearchScope.SUB);

            for (T obj = objects.next(); obj != null; obj = objects.next()) {
                results.add(obj);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return results;

    }

    public <T> boolean add(T object, Class<T> clazz, String parentDn) {

        boolean success = false;
        try {
            LDAPPersister<T> persister = LDAPPersister.getInstance(clazz);
            LDAPResult ldapResult = persister.add(object, ldapOperationService.getConnection(), parentDn);
            success = ldapResult.getResultCode().equals(ResultCode.SUCCESS);
            logger.trace("add. Operation result was '{}'", ldapResult.getResultCode().getName());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public <T> boolean modify(T object, Class<T> clazz) {

        boolean success = false;
        try {
            LDAPPersister<T> persister = LDAPPersister.getInstance(clazz);
            LDAPResult ldapResult = persister.modify(object, ldapOperationService.getConnection(), null, true);
            if (ldapResult == null) {
                logger.trace("modify. No attribute changes took place for this modification");
            } else {
                success = ldapResult.getResultCode().equals(ResultCode.SUCCESS);
                logger.trace("modify. Operation result was '{}'", ldapResult.getResultCode().getName());
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public <T> boolean delete(T object, Class<T> clazz) {

        boolean success = false;
        try {
            LDAPPersister<T> persister = LDAPPersister.getInstance(clazz);
            LDAPResult ldapResult = persister.delete(object, ldapOperationService.getConnection());
            success = ldapResult.getResultCode().equals(ResultCode.SUCCESS);
            logger.trace("delete. Operation result was '{}'", ldapResult.getResultCode().getName());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    /**
     * Tries to determine whether local installation of Gluu is using a backend LDAP. This reads the OxTrust configuration
     * Json and inspects inside property "sourceConfigs"
     * @return
     * @throws Exception
     */
    public boolean isBackendLdapEnabled() {

        try {
            if (oxTrustConfCacheRefresh != null) {
                List<Boolean> enabledList = new ArrayList<>();
                oxTrustConfCacheRefresh.get("sourceConfigs").forEach(node -> enabledList.add(node.get("enabled").asBoolean()));
                return enabledList.stream().anyMatch(Boolean::booleanValue);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return false;

    }
    public boolean authenticate(String uid, String pass) throws Exception {
        if (oxTrustConfApplication != null) {
            return ldapOperationService.authenticate(uid, pass, oxTrustConfApplication.get("baseDN").asText());
        }
        throw new UnsupportedOperationException("LDAP authentication is not supported with current settings");
    }

    private boolean loadApplianceSettings(Properties properties) {

        boolean success = false;
        try {
            loadOxAuthSettings(properties.getProperty("oxauth_ConfigurationEntryDN"));
            success = true;
            String dn = properties.getProperty("oxtrust_ConfigurationEntryDN");
            if (dn != null) {
                loadOxTrustSettings(dn);
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    private void loadOxAuthSettings(String dn) throws Exception {

        oxAuthConfiguration conf = get(oxAuthConfiguration.class, dn);
        oxAuthConfDynamic = mapper.readTree(conf.getAuthConfDynamic());
        oxAuthConfStatic = mapper.readTree(conf.getAuthConfStatic());


    }

    private void loadOxTrustSettings(String dn) throws Exception {
        oxTrustConfiguration confT = get(oxTrustConfiguration.class, dn);
        if (confT != null) {
            oxTrustConfApplication = mapper.readTree(confT.getOxTrustConfApplication());
            oxTrustConfCacheRefresh = mapper.readTree(confT.getOxTrustConfCacheRefresh());
        }
    }

}
