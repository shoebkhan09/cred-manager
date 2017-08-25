package org.gluu.credmanager.services.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.LdapSettings;
import org.gluu.credmanager.core.credential.SecurityKey;;
import org.gluu.credmanager.services.ldap.pojo.*;
import org.gluu.site.ldap.LDAPConnectionProvider;
import org.gluu.site.ldap.OperationsFacade;
import org.xdi.util.properties.FileConfiguration;
import org.xdi.util.security.PropertiesDecrypter;
import org.xdi.util.security.StringEncrypter;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import java.text.MessageFormat;
import java.util.*;
import java.util.List;

/**
 * Created by jgomer on 2017-07-06.
 * An app. scoped bean that executes operations on local LDAP. Before usage, setup method has to be called (initializes
 * the connection) - this is done only once during app. start (see AppConfiguration bean).
 * Most operations (methods) in this class are self explanatory
 */
@ApplicationScoped
public class LdapService {

    private static final String OTP_SCRIPT_BRANCH_PATTERN="inum={0}!0011!5018.D4BF,ou=scripts,o={0},o=gluu";
    private static final String SMS_SCRIPT_BRANCH_PATTERN="inum={0}!0011!09A0.93D6,ou=scripts,o={0},o=gluu";
    private Logger logger = LogManager.getLogger(getClass());

    private Properties ldapProperties;
    private CustomEntryManager ldapEntryManager;
    private LdapSettings ldapSettings;
    private ObjectMapper mapper;

    private OxTrustConfiguration oxTrustConfig =null;
    private JsonNode oxAuthConfDynamic;

    /**
     * Initializes and LdapEntryManager instance for operation
     * @param settings LDAP settings as entered into the global configuration file
     * @throws Exception
     */
    public void setup(LdapSettings settings) throws Exception{

        mapper = new ObjectMapper();
        ldapSettings=settings;

        String saltFile=ldapSettings.getSaltLocation();
        ldapProperties = new FileConfiguration(ldapSettings.getOxLdapLocation()).getProperties();
        if (saltFile != null){
            String salt = new FileConfiguration(saltFile).getProperties().getProperty("encodeSalt");
            ldapProperties = PropertiesDecrypter.decryptProperties(StringEncrypter.instance(salt), ldapProperties);
        }
        LDAPConnectionProvider connProvider = new LDAPConnectionProvider(ldapProperties);

        OperationsFacade facade = new OperationsFacade(connProvider);   //bindConnProvider??
        ldapEntryManager=new CustomEntryManager(facade);

        if (ldapEntryManager==null)
            throw new Exception(Labels.getLabel("app.bad_ldapentrymanager"));
    }

    //TODO: refactor these methods to call .find(class,dn) - search in oxauth proper class with the suitable annotations
    public String getOrganizationName() throws Exception{

        String dn=String.format("o=%s,o=gluu", ldapSettings.getOrgInum());
        List<GluuOrganization> list=ldapEntryManager.findEntries(dn, GluuOrganization.class,null);
        return list.get(0).getName();

    }

    private OxTrustConfiguration getOxTrustConfig(){

        if (oxTrustConfig ==null){
            String dn=String.format("ou=oxtrust,ou=configuration,inum=%s,ou=appliances,o=gluu", ldapSettings.getApplianceInum());
            List<OxTrustConfiguration> list=ldapEntryManager.findEntries(dn, OxTrustConfiguration.class,null);
            oxTrustConfig =list.get(0);
        }
        return oxTrustConfig;
    }

    private JsonNode getOxAuthConfDynamic() throws Exception{

        if (oxAuthConfDynamic==null){
            String dn=String.format("ou=oxauth,ou=configuration,inum=%s,ou=appliances,o=gluu", ldapSettings.getApplianceInum());
            List<OxAuthConfiguration> list=ldapEntryManager.findEntries(dn, OxAuthConfiguration.class,null);
            oxAuthConfDynamic = mapper.readTree(list.get(0).getStrConfDynamic());
        }
        return oxAuthConfDynamic;
    }

    public GluuPerson getGluuPerson(String rdn) throws Exception{
        String dn=String.format("%s,ou=people,o=%s,o=gluu", rdn, ldapSettings.getOrgInum());
        return ldapEntryManager.find(GluuPerson.class, dn);
    }

    /**
     * Tries to determine whether local installation of Gluu is using a backend LDAP. This reads the OxTrust configuration
     * Json and inspects inside property "sourceConfigs"
     * @return
     * @throws Exception
     */
    public boolean isBackendLdapEnabled() throws Exception{

        JsonNode tree=mapper.readTree(getOxTrustConfig().getConfCacheRefreshStr());

        List<Boolean> enabledList=new ArrayList<>();
        tree.get("sourceConfigs").forEach(node -> enabledList.add(node.get("enabled").asBoolean()));
        return enabledList.stream().anyMatch(item -> item);

    }

    public String getOIDCEndpoint() throws Exception{
        return getOxAuthConfDynamic().get("openIdConfigurationEndpoint").asText();
    }

    public String getIssuerUrl() throws Exception{
        return getOxAuthConfDynamic().get("issuer").asText();
    }

    public List<SecurityKey> getFidoDevices(String userRdn){
        String dn=String.format("%s,ou=people,o=%s,o=gluu", userRdn, ldapSettings.getOrgInum());
        return ldapEntryManager.findEntries(String.format("ou=fido,%s", dn), SecurityKey.class, null);
    }

    /**
     * Updates in LDAP the attributes that store the raw mobile phones as well as the Json representation that contains
     * the credential information associated to those phones for the person being referenced
     * @param userRdn LDAP RDN of user
     * @param phones List of current numbers (strings only)
     * @param jsonPhones A Json representation of an array of VerifiedPhones. The information related to parameter phone
     *                   is already included here
     * @throws Exception
     */
    public void updateMobilePhones(String userRdn, List<String> phones, String jsonPhones) throws Exception{

        GluuPerson person=getGluuPerson(userRdn);
        person.setMobileNumbers(phones);
        person.setVerifiedPhonesJson(jsonPhones);
        ldapEntryManager.merge(person);

    }

    public void updateOTPDevices(String userRdn, List<String> devs, String jsonDevs) throws Exception{

        GluuPerson person=getGluuPerson(userRdn);
        person.setExternalUids(devs);
        person.setOtpDevicesJson(jsonDevs);
        ldapEntryManager.merge(person);

    }

    public void updatePreferredMethod(String userRdn, String method) throws Exception{
        GluuPerson person=getGluuPerson(userRdn);
        person.setPreferredAuthMethod(method);
        ldapEntryManager.merge(person);
    }

    public boolean authenticate(String uid, String pass) throws Exception{
        JsonNode baseDnNode=mapper.readTree(getOxTrustConfig().getConfApplicationStr()).get("baseDN");
        return ldapEntryManager.authenticate(uid, pass, baseDnNode.asText());
    }

    public void changePassword(String userRdn, String newPassword) throws Exception{
        GluuPerson person=getGluuPerson(userRdn);
        person.setPass(newPassword);
        ldapEntryManager.merge(person);
    }

    public CustomScript getOTPScriptInfo() throws Exception{
        String dn= MessageFormat.format(OTP_SCRIPT_BRANCH_PATTERN, ldapSettings.getOrgInum());
        return ldapEntryManager.find(CustomScript.class, dn);
    }

    public CustomScript getSmsScriptInfo() throws Exception{
        String dn= MessageFormat.format(SMS_SCRIPT_BRANCH_PATTERN, ldapSettings.getOrgInum());
        return ldapEntryManager.find(CustomScript.class, dn);
    }

    public void createFidoBranch(String userRdn){

        String dn=String.format("ou=fido,%s,ou=people,o=%s,o=gluu", userRdn, ldapSettings.getOrgInum());
        OUEntry entry=null;
        try {
            entry = ldapEntryManager.find(OUEntry.class, dn);
        }
        catch (Exception e){
            logger.info(Labels.getLabel("app.no_fido_branch"), userRdn);
            entry=new OUEntry();
            entry.setDn(dn);
            entry.setOu("fido");
            ldapEntryManager.persist(entry);
        }

    }

    public SecurityKey relocateFidoDevice(String userRdn, long time) throws Exception {

        String orgy=ldapSettings.getOrgInum();
        String registeredBranch=String.format("ou=registered_devices,ou=u2f,o=%s,o=gluu", orgy);
        List<SecurityKey> keys=ldapEntryManager.findEntries(registeredBranch, SecurityKey.class,null);
        long diffs[]=keys.stream().mapToLong(key -> time-key.getCreationDate().getTime()).toArray();

        //Search for the smallest time difference
        Pair<Long, Integer> min=new Pair<>(Long.MAX_VALUE, -1);
        for (int i=0;i<diffs.length;i++)
            if (diffs[i]>=0 && min.getX()>diffs[i])  //Only search non-negative differences
                min=new Pair<>(diffs[i], i);

        SecurityKey key=keys.get(min.getY());   //pick device to move under own user branch
        key.setDn(String.format("oxId=%s,ou=fido,%s,ou=people,o=%s,o=gluu", key.getId(), userRdn, orgy));
        ldapEntryManager.persist(key);

        //This is not necessary as LDAP is cleaned often but good in testing environment
        SecurityKey oldkey=new SecurityKey();
        oldkey.setDn(String.format("oxId=%s,%s", key.getId(), registeredBranch));
        ldapEntryManager.remove(oldkey);

        return key;

    }

    public void updateU2fDevice(SecurityKey key) throws Exception{
        ldapEntryManager.merge(key);
    }

    public void removeU2fDevice(SecurityKey key) throws Exception{
        ldapEntryManager.remove(key);
    }
}
