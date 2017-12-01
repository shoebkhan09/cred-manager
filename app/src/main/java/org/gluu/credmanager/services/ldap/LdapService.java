package org.gluu.credmanager.services.ldap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unboundid.ldap.sdk.Filter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.LdapSettings;
import org.gluu.credmanager.core.credential.SuperGluuDevice;
import org.gluu.credmanager.core.credential.FidoDevice;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.pojo.*;
import org.gluu.site.ldap.LDAPConnectionProvider;
import org.gluu.site.ldap.OperationsFacade;
import org.xdi.oxauth.model.fido.u2f.DeviceRegistrationStatus;
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

    public static final String MOBILE_PHONE_ATTR="mobile";
    public static final String PREFERRED_METHOD_ATTR="oxPreferredMethod";
    public static final String OTP_DEVICES_ATTR="oxOTPDevices";
    public static final String MOBILE_DEVICES_ATTR="oxMobileDevices";

    private Logger logger = LogManager.getLogger(getClass());

    private Properties ldapProperties;
    private CustomEntryManager ldapEntryManager;
    private LdapSettings ldapSettings;
    private ObjectMapper mapper;

    private GluuOrganization organization=null;
    private OxTrustConfiguration oxTrustConfig=null;
    private JsonNode oxAuthConfDynamic;
    private String usersDN;
    private int dynamicClientExpirationTime=-1;

    /**
     * Initializes and LdapEntryManager instance for operation
     * @param settings LDAP settings as entered into the global configuration file
     * @throws Exception
     */
    public void setup(LdapSettings settings) throws Exception{

        try {
            mapper = new ObjectMapper();
            ldapSettings = settings;

            ldapProperties = new FileConfiguration(ldapSettings.getOxLdapLocation()).getProperties();
            String saltFile = ldapSettings.getSaltLocation();
            if (Utils.stringOptional(saltFile).isPresent()) {
                String salt = new FileConfiguration(saltFile).getProperties().getProperty("encodeSalt");
                ldapProperties = PropertiesDecrypter.decryptProperties(StringEncrypter.instance(salt), ldapProperties);
            }
            LDAPConnectionProvider connProvider = new LDAPConnectionProvider(ldapProperties);

            OperationsFacade facade = new OperationsFacade(connProvider);   //bindConnProvider??
            ldapEntryManager = new CustomEntryManager(facade);

            //Initialize important class members
            loadApplianceSettings(ldapSettings);
            loadOrganizationSettings(ldapSettings);
        }
        catch (Exception e) {
            logger.error(e.getMessage(), e);
            throw new Exception(Labels.getLabel("app.wrong_ldap_settings"));
        }

    }

    public void loadOrganizationSettings(LdapSettings settings) throws IOException{

        String dn=String.format("o=%s,o=gluu", settings.getOrgInum());
        organization=ldapEntryManager.find(GluuOrganization.class, dn);
        if (organization==null)
            throw new IOException();

        usersDN="ou=people," + dn;

    }

    public void loadApplianceSettings(LdapSettings settings) throws IOException{
        String dn=String.format("ou=configuration,inum=%s,ou=appliances,o=gluu", settings.getApplianceInum());

        OxAuthConfiguration authConfig=ldapEntryManager.find(OxAuthConfiguration.class, "ou=oxauth," + dn);
        if (authConfig==null)   //Prevent having a useless NPE
            throw new IOException();

        oxAuthConfDynamic = mapper.readTree(authConfig.getStrConfDynamic());
        boolean dynRegEnabled =oxAuthConfDynamic.get("dynamicRegistrationEnabled").asBoolean();
        dynamicClientExpirationTime= dynRegEnabled ? oxAuthConfDynamic.get("dynamicRegistrationExpirationTime").asInt() : -1;

        oxTrustConfig=ldapEntryManager.find(OxTrustConfiguration.class, "ou=oxtrust," + dn);

    }

    public String getOrganizationName() throws Exception{
        return organization.getName();
    }

    public int getDynamicClientExpirationTime() {
        return dynamicClientExpirationTime;
    }

    public boolean belongsToManagers(String userRdn) throws Exception{

        boolean belongs=false;
        String inum=organization.getManagerGroupInum();
        List<String> memberships=getGluuPerson(userRdn).getMemberships();

        if (memberships!=null && memberships.size()>0)
            belongs=memberships.stream().anyMatch(membership -> membership.equals(inum));

        return belongs;

    }

    public GluuPerson getGluuPerson(String rdn) throws Exception{
        String dn=String.format("%s,%s", rdn, usersDN);
        return ldapEntryManager.find(GluuPerson.class, dn);
    }

    public List<GluuPerson> getPeopleById(List<String> uids) throws Exception{
        Stream<Filter> filterStream=uids.stream().map(uid -> Filter.createEqualityFilter("uid", uid));
        Filter filter=Filter.createORFilter(filterStream.collect(Collectors.toList()));
        return ldapEntryManager.findEntries(usersDN, GluuPerson.class,  filter);
    }

    /**
     * Tries to determine whether local installation of Gluu is using a backend LDAP. This reads the OxTrust configuration
     * Json and inspects inside property "sourceConfigs"
     * @return
     * @throws Exception
     */
    public boolean isBackendLdapEnabled() throws Exception{

        if (oxTrustConfig!=null) {
            JsonNode tree = mapper.readTree(oxTrustConfig.getConfCacheRefreshStr());

            List<Boolean> enabledList = new ArrayList<>();
            tree.get("sourceConfigs").forEach(node -> enabledList.add(node.get("enabled").asBoolean()));
            return enabledList.stream().anyMatch(item -> item);
        }
        return false;

    }

    public String getOIDCEndpoint() throws Exception{
        return oxAuthConfDynamic.get("openIdConfigurationEndpoint").asText();
    }

    public String getIssuerUrl() throws Exception{
        return oxAuthConfDynamic.get("issuer").asText();
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
        if (oxTrustConfig!=null) {
            JsonNode baseDnNode = mapper.readTree(oxTrustConfig.getConfApplicationStr()).get("baseDN");
            return ldapEntryManager.authenticate(uid, pass, baseDnNode.asText());
        }
        throw new UnsupportedOperationException(Labels.getLabel("app.ldap_authn_unsupported"));
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

        String dn=String.format("ou=fido,%s,%s", userRdn, usersDN);
        OUEntry entry;
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

    /**
     * Returns a list of FidoDevice instances found under the given branch that matches the oxApplication value given and
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
                Filter.createEqualityFilter("oxStatus", DeviceRegistrationStatus.ACTIVE.getValue())));
        String dn=String.format("ou=fido,%s,%s", userRdn, usersDN);
        return ldapEntryManager.findEntries(dn, clazz, filter);
    }

    public void updateFidoDevice(FidoDevice device) throws Exception{
        ldapEntryManager.merge(device);
    }

    public void removeFidoDevice(FidoDevice device) throws Exception{
        ldapEntryManager.remove(device);
    }

    public <T extends FidoDevice> T getFidoDevice(String userRdn, long time, String oxApp, Class<T> clazz) throws Exception{
        List<T> list=getU2FDevices(userRdn, oxApp, clazz);
        logger.debug("getFidoDevice. list is {}", list.stream().map(d -> d.getId()).collect(Collectors.toList()).toString());
        return Utils.getRecentlyCreatedDevice(list, time);
    }

    public List<String> getSGDevicesIDs(String oxApplication) throws Exception{

        Filter filter=Filter.createANDFilter(Arrays.asList(
                Filter.createEqualityFilter("oxApplication", oxApplication),
                Filter.createEqualityFilter("oxStatus", DeviceRegistrationStatus.ACTIVE.getValue())));

        List<SuperGluuDevice> list=ldapEntryManager.findEntries(usersDN, SuperGluuDevice.class, new String[]{"oxDeviceData"}, filter);
        return list.stream().filter(d -> d.getDeviceData()!=null).map(d -> d.getDeviceData().getUuid()).collect(Collectors.toList());

    }

    public List<String> getPhoneNumbers() throws Exception{
        List<GluuPerson> list=ldapEntryManager.findEntries(usersDN, GluuPerson.class, new String[]{MOBILE_PHONE_ATTR},null);
        Stream<GluuPerson> mobilePeople=list.stream().filter(person -> person.getMobileNumbers()!=null);
        return mobilePeople.flatMap(person -> person.getMobileNumbers().stream()).collect(Collectors.toList());
    }

    public void storeUserEnrollmentCode(String userRdn, String code) throws Exception{
        GluuPerson person=getGluuPerson(userRdn);
        person.setTemporaryEnrollmentCode(code);
        ldapEntryManager.merge(person);
    }

    public void cleanRandEnrollmentCode(String userRdn) throws Exception{
        GluuPerson person=getGluuPerson(userRdn);

        if (person.getTemporaryEnrollmentCode()!=null) {    //clean if it's present
            person.setTemporaryEnrollmentCode(null);
            ldapEntryManager.merge(person);
        }
    }

    public <T> List<T> findUsersWithPreferred(String searchString, String attributes[], Class<T> cls) throws Exception{

        Stream<Filter> stream=Arrays.asList(attributes).stream()
                .map(attr -> Filter.createSubstringFilter(attr, null, new String[]{searchString}, null));

        Filter filter=Filter.createANDFilter(Arrays.asList(
                Filter.createORFilter(stream.collect(Collectors.toList())),
                Filter.createPresenceFilter(PREFERRED_METHOD_ATTR)
        ));

        return ldapEntryManager.findEntries(usersDN, cls, attributes, filter);

    }

    public int preferenceCount(List<String> methods) throws Exception{

        int total=0;
        for (String method : methods){
            Filter filter=Filter.createEqualityFilter(PREFERRED_METHOD_ATTR, method);
            List<GluuPerson> list=ldapEntryManager.findEntries(usersDN, GluuPerson.class, new String[]{"dn"}, filter);
            total+=list.size();
        }
        return total;

    }

}