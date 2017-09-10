package org.gluu.credmanager.services.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.ldap.sdk.Filter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.LdapSettings;
import org.gluu.credmanager.core.credential.SecurityKey;;
import org.gluu.credmanager.core.credential.SuperGluuDevice;
import org.gluu.credmanager.core.credential.fido.FidoDevice;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.pojo.*;
import org.gluu.site.ldap.LDAPConnectionProvider;
import org.gluu.site.ldap.OperationsFacade;
import org.xdi.util.properties.FileConfiguration;
import org.xdi.util.security.PropertiesDecrypter;
import org.xdi.util.security.StringEncrypter;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-07-06.
 * An app. scoped bean that executes operations on local LDAP. Before usage, setup method has to be called (initializes
 * the connection) - this is done only once during app. start (see AppConfiguration bean).
 * Most operations (methods) in this class are self explanatory
 */
@ApplicationScoped
public class LdapService {

    private Logger logger = LogManager.getLogger(getClass());

    private Properties ldapProperties;
    private CustomEntryManager ldapEntryManager;
    private LdapSettings ldapSettings;
    private ObjectMapper mapper;

    private OxTrustConfiguration oxTrustConfig=null;
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

    public CustomScript getCustomScript(String acr) throws Exception{

        Filter filter=Filter.createEqualityFilter("displayName", acr);
        String baseDN=String.format("ou=scripts,o=%s,o=gluu", ldapSettings.getOrgInum());
        List<CustomScript> list=ldapEntryManager.findEntries(baseDN, CustomScript.class, filter);

        int size=list.size();
        if (size==1)
            return list.get(0);
        else
            throw new IOException(Labels.getLabel("app.script_lookup_error", new Object[]{size, acr}));
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

    public SecurityKey relocateU2fDevice(String userRdn, long time) throws Exception {

        String orgy=ldapSettings.getOrgInum();
        String registeredBranch=String.format("ou=registered_devices,ou=u2f,o=%s,o=gluu", orgy);
        List<SecurityKey> keys=ldapEntryManager.findEntries(registeredBranch, SecurityKey.class,null);

        SecurityKey key=Utils.getRecentlyCreatedDevice(keys, time);   //pick device to move under own user branch
        if (key!=null) {
            key.setDn(String.format("oxId=%s,ou=fido,%s,ou=people,o=%s,o=gluu", key.getId(), userRdn, orgy));
            ldapEntryManager.persist(key);

            //This is not necessary as LDAP is cleaned often but a need in testing environment
            SecurityKey oldkey = new SecurityKey();
            oldkey.setDn(String.format("oxId=%s,%s", key.getId(), registeredBranch));
            ldapEntryManager.remove(oldkey);
        }
        return key;

    }

    /**
     * Returns a list of FidoDevice instances found under the given branch that matches de oxApplication value given and
     * whose oxStatus attribute equals to "active"
     * @param userRdn Branch under the query is performed
     * @param oxApplication Value to match for oxApplication attribute (see LDAP object class oxDeviceRegistration)
     * @param clazz Any subclass of FidoDevice
     * @param <T>
     * @return List of FidoDevices
     */
    public <T extends FidoDevice> List<T> getU2FDevices(String userRdn, String oxApplication, Class<T> clazz) throws Exception{
        Filter filter=Filter.createANDFilter(Arrays.asList(
                Filter.createEqualityFilter("oxApplication", oxApplication),
                Filter.createEqualityFilter("oxStatus", "active")));
        String dn=String.format("%s,ou=people,o=%s,o=gluu", userRdn, ldapSettings.getOrgInum());
        return ldapEntryManager.findEntries(String.format("ou=fido,%s", dn), clazz, filter);
    }

    public void updateFidoDevice(FidoDevice device) throws Exception{
        ldapEntryManager.merge(device);
    }

    public void removeFidoDevice(FidoDevice device) throws Exception{
        ldapEntryManager.remove(device);
    }

    public SuperGluuDevice getSuperGluuDevice(String userRdn, long time, String oxApp) throws Exception{
        List<SuperGluuDevice> list=getU2FDevices(userRdn, oxApp, SuperGluuDevice.class);
logger.debug("list is {}", list.stream().map(d -> d.getDeviceData().getUuid()).collect(Collectors.toList()).toString());
        return Utils.getRecentlyCreatedDevice(list, time);
    }

    public List<String> getSGDevicesIDs(String oxApplication) throws Exception{

        Filter filter=Filter.createANDFilter(Arrays.asList(
                Filter.createEqualityFilter("oxApplication", oxApplication),
                Filter.createEqualityFilter("oxStatus", "active")));
        String dn=String.format("ou=people,o=%s,o=gluu", ldapSettings.getOrgInum());

        List<SuperGluuDevice> list=ldapEntryManager.findEntries(dn, SuperGluuDevice.class, new String[]{"oxDeviceData"}, filter);
        return list.stream().filter(d -> d.getDeviceData()!=null).map(d -> d.getDeviceData().getUuid()).collect(Collectors.toList());

    }

    public List<String> getPhoneNumbers() throws Exception{
        String dn=String.format("ou=people,o=%s,o=gluu", ldapSettings.getOrgInum());
        List<GluuPerson> list=ldapEntryManager.findEntries(dn, GluuPerson.class, new String[]{"mobile"},null);
        Stream<GluuPerson> mobilePeople=list.stream().filter(person -> person.getMobileNumbers()!=null);
        return mobilePeople.flatMap(person -> person.getMobileNumbers().stream()).collect(Collectors.toList());
    }

}