/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.credential.*;
import org.gluu.credmanager.core.credential.FidoDevice;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.ldap.pojo.GluuPerson;
import org.gluu.credmanager.conf.CredentialType;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.gluu.credmanager.conf.CredentialType.*;

/**
 * Created by jgomer on 2017-07-16.
 * An app. scoped bean that encapsulates logic related to users manipulation (CRUD) at memory level (no LDAP storage)
 */
@ApplicationScoped
public class UserService {

    /*
    The list of OpenId scopes required to be able to inspect the claims needed. See attributes of User class
     */
    public static final String[] requiredOpenIdScopes = new String[]{"openid","profile","user_name","clientinfo"};
        //,"email","mobile_phone","phone"

    @Inject
    AppConfiguration appConfiguration;

    @Inject
    LdapService ldapService;

    private Logger logger = LogManager.getLogger(getClass());
    private ObjectMapper mapper=new ObjectMapper();

    /**
     * Creates a User instance from a set of OpenId claims
     * @param claims
     * @return
     */
    public User createUserFromClaims(Map<String, List<String>> claims){

        User u = new User();
        u.setUserName(getClaim(claims,"user_name"));
        logger.trace("createUserFromClaims. Username is {}", u.getUserName());

        u.setGivenName(getClaim(claims,"given_name"));
        //u.setEmail(getClaim(claims,"email"));
        //u.setPhone(getClaim(claims, "phone_number_verified"));    scopes: `email`, `mobile_phone`, `phone`,
        //u.setMobilePhones(claims.get("phone_mobile_number"));

        String inum=getClaim(claims, "inum");
        if (inum!=null)
            u.setRdn("inum=" + inum);

        if (u.getRdn()==null || u.getUserName()==null) {
            u = null;
            logger.error("createUserFromClaims. Could not obtain user claims!");
        }

        return u;
    }

    /**
     * From a collection of claims, it extracts the first value found for a claim whose name is passed. If claim is not
     * found or has an empty list associated, it returns null
     * @param claims Map with claims (as gathered via oxd)
     * @param claimName Claim to inspect
     * @return First value of claim or null
     */
    private String getClaim(Map<String, List<String>> claims, String claimName){
        List<String> values=claims.get(claimName);
        return (values==null || values.size()==0) ? null : values.get(0);
    }

    public CredentialType getPreferredMethod(User user){

        CredentialType cred=null;
        try {
            String pref= ldapService.getGluuPerson(user.getRdn()).getPreferredAuthMethod();
            cred=(pref==null) ? null : CredentialType.get(pref);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return cred;

    }

    public boolean setPreferredMethod(User user, CredentialType cred){

        boolean success=false;
        try {
            ldapService.updatePreferredMethod(user.getRdn(), (cred==null) ? null : cred.getName());
            user.setPreference(cred);
            success=true;
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    public boolean currentPasswordMatch(User user, String password) throws Exception{
        return ldapService.authenticate(user.getUserName(), password);
    }

    public boolean changePassword(User user, String newPassword){

        boolean success=false;
        try {
            if (Utils.stringOptional(newPassword).isPresent()) {
                ldapService.changePassword(user.getRdn(), newPassword);
                success = true;
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return success;

    }

    /**
     * Creates an instance of OTPDevice by looking up in the list of OTPDevices passed. If the item is not found in the
     * in the list, it means the device was previously enrolled by using a different application. In this case the resulting
     * object will not have properties like nickname, etc. Just a basic ID
     * @param uid Identifier of an OTP device (LDAP attribute "oxExternalUid" inside a user entry)
     * @param list List of existing OTP devices enrolled. Ideally, there is an item here corresponding to the uid passed
     * @return OTPDevice object
     */
    private OTPDevice getExtraOTPInfo(String uid, List<OTPDevice> list){
        //Complements current otp device with extra info in the list if any

        OTPDevice device=new OTPDevice(uid);
        int hash=device.getId();

        Optional<OTPDevice> extraInfoOTP=list.stream().filter(dev -> dev.getId()==hash).findFirst();
        if (extraInfoOTP.isPresent()) {
            device.setAddedOn(extraInfoOTP.get().getAddedOn());
            device.setNickName(extraInfoOTP.get().getNickName());
        }
        return device;

    }

    /**
     * Creates an instance of VerifiedPhone by looking up in the list of VerifiedPhones passed. If the item is not found
     * in the list, it means the user had already that phone added by means of another application, ie. oxTrust. In this
     * case the resulting object will not have properties like nickname, etc. Just the phone number
     * @param number Phone number (LDAP attribute "mobile" inside a user entry)
     * @param list List of existing phones enrolled. Ideally, there is an item here corresponding to the uid number passed
     * @return VerifiedPhone object
     */
    private VerifiedPhone getExtraPhoneInfo(String number, List<VerifiedPhone> list){
        //Complements current phone with extra info in the list if any
        VerifiedPhone phone=new VerifiedPhone(number);

        Optional<VerifiedPhone> extraInfoPhone=list.stream().filter(ph -> number.equals(ph.getNumber())).findFirst();
        if (extraInfoPhone.isPresent()) {
            phone.setAddedOn(extraInfoPhone.get().getAddedOn());
            phone.setNickName(extraInfoPhone.get().getNickName());
        }
        return phone;
    }

    private List<VerifiedPhone> getVerifiedPhones(GluuPerson person){

        try{
            String json=person.getVerifiedPhonesJson();
            Optional<String> optJson= Utils.stringOptional(json);

            if (optJson.isPresent())
                json=mapper.readTree(json).get("phones").toString();
            else
                json="[]";

            List<VerifiedPhone> vphones=mapper.readValue(json, new TypeReference<List<VerifiedPhone>>(){});

            logger.trace("getVerifiedPhones. Phones from ldap: {}", vphones);
            Stream<VerifiedPhone> stream=person.getMobileNumbers().stream().map(str -> getExtraPhoneInfo(str, vphones));

            return stream.collect(Collectors.toList());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    private List<OTPDevice> getOtpDevices(GluuPerson person){

        try {
            String json = person.getOtpDevicesJson();
            Optional<String> optJson = Utils.stringOptional(json);

            if (optJson.isPresent())
                json = mapper.readTree(json).get("devices").toString();
            else
                json = "[]";

            List<OTPDevice> devs=mapper.readValue(json, new TypeReference<List<OTPDevice>>(){});

            Stream<OTPDevice> stream=person.getExternalUids().stream().map(uid-> getExtraOTPInfo(uid, devs));
            return stream.collect(Collectors.toList());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return new ArrayList<>();
        }

    }

    private List<FidoDevice> getFidoDevices(String rdn, String appId, Class clazz){

        try{
            return ldapService.getU2FDevices(rdn, appId, clazz);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Computes the current users's enrolled credentials creating a mapping of credential type vs. list of credentials
     * @param user The User subject of retrieval
     * @return A Map with user's credentials. Only returned
     */
    public Map<CredentialType, List<RegisteredCredential>> getPersonalCredentials(User user){

        try {
            String rdn = user.getRdn();
            GluuPerson person=ldapService.getGluuPerson(rdn);
            Utils.emptyNullLists(person, ArrayList::new);

            Map<CredentialType, List<RegisteredCredential>> allCreds=new HashMap<>();
            Set<CredentialType> enabled=appConfiguration.getEnabledMethods();

            if (enabled.contains(VERIFIED_PHONE))
                allCreds.put(VERIFIED_PHONE, Utils.mapSortCollectList(getVerifiedPhones(person), RegisteredCredential.class::cast));

            if (enabled.contains(OTP))
                allCreds.put(OTP, Utils.mapSortCollectList(getOtpDevices(person), RegisteredCredential.class::cast));

            if (enabled.contains(SECURITY_KEY) || enabled.contains(SUPER_GLUU)) {
                ldapService.createFidoBranch(rdn);
                cleanRandEnrollmentCode(user);
                String appId;

                if (enabled.contains(SECURITY_KEY)) {
                    appId = appConfiguration.getConfigSettings().getU2fSettings().getAppId();
                    allCreds.put(SECURITY_KEY, Utils.mapSortCollectList(getFidoDevices(rdn, appId, SecurityKey.class), RegisteredCredential.class::cast));
                }
                if (enabled.contains(SUPER_GLUU)) {
                    appId = appConfiguration.getConfigSettings().getOxdConfig().getRedirectUri();
                    allCreds.put(SUPER_GLUU, Utils.mapSortCollectList(getFidoDevices(rdn, appId, SuperGluuDevice.class), RegisteredCredential.class::cast));
                }
            }
            return allCreds;
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return null;
        }

    }

    /**
     * Returns a set of CredentialType such that every element in the set has at least one corresponding enrolled credential
     * for this user. This set has to a subset of one conformed by all currently enabled credential types
     * @param user The user for which the set will be generated
     * @return The set referred above.
     */
    public Set<CredentialType> getEffectiveMethods(User user){
        //Get those credentials types that have associated at least one item
        Stream<Map.Entry<CredentialType, List<RegisteredCredential>>> stream=user.getCredentials().entrySet().stream();
        //Create a sorted set from it
        Set<CredentialType> set=stream.filter(e -> e.getValue().size()>0).map(Map.Entry::getKey).collect(Collectors.toCollection(TreeSet::new));
        return set;
    }

    /**
     * Stores in LDAP the mobile phones passed as parameters for this user (the raw mobile numbers are stored in the
     * proper multivalued LDAP attribute). This class builds a json representation with additional information associated
     * to every raw phone number so it can also be saved
     * @param user The user to whom the list of phones belongs to (mobiles parameter)
     * @param mobiles A list of RegisteredCredential instances
     * @param newPhone An additional RegisteredCredential that is also saved. If storing is successful this element is
     *                 appended to the list mobiles
     * @throws Exception
     */
    public void updateMobilePhonesAdd(User user, List<RegisteredCredential> mobiles, VerifiedPhone newPhone) throws Exception{

        //See getVerifiedPhones() above
        List<VerifiedPhone> vphones=new ArrayList<>(Utils.mapCollectList(mobiles, VerifiedPhone.class::cast));
        if (newPhone!=null)
            vphones.add(newPhone);

        String json=null;
        List<String> strPhones=vphones.stream().map(VerifiedPhone::getNumber).collect(Collectors.toList());
        if (strPhones.size()>0)
            json=String.format("{%sphones%s: %s}", "\"", "\"", mapper.writeValueAsString(vphones));

        ldapService.updateMobilePhones(user.getRdn(), strPhones, json);
        if (newPhone!=null)
            //modify list only if LDAP update took place
            mobiles.add(newPhone);

    }

    /**
     * Stores in LDAP the HOTP/TOTP devices passed as parameters for this user (the oxExternalUIDs of these devices are
     * saved in their corresponding multivalued LDAP attribute). This class builds a json representation with additional
     * information associated to every oxExternalUID so it can also be saved
     * @param user The user to whom the list of devices belongs to (devs parameter)
     * @param devs A list of RegisteredCredential instances
     * @param newDevice An additional RegisteredCredential that is also saved. If storing is successful this element is
     *                 appended to the list devs
     * @throws Exception
     */
    public void updateOTPDevicesAdd(User user, List<RegisteredCredential> devs, OTPDevice newDevice) throws Exception{

        //See getOTPDDevices() above
        List<OTPDevice> vdevices=new ArrayList<>(Utils.mapCollectList(devs, OTPDevice.class::cast));
        if (newDevice!=null)
            vdevices.add(newDevice);

        String json=null;
        List<String> strDevs=vdevices.stream().map(OTPDevice::getUid).collect(Collectors.toList());
        if (strDevs.size()>0)
            json=String.format("{%sdevices%s: %s}", "\"", "\"", mapper.writeValueAsString(vdevices));

        ldapService.updateOTPDevices(user.getRdn(), strDevs, json);
        if (newDevice!=null)
            //modify list only if LDAP update took place
            devs.add(newDevice);

    }

    public void updateFidoDevice(FidoDevice dev) throws Exception{
        ldapService.updateFidoDevice(dev);
    }

    public void removeFidoDevice(FidoDevice dev) throws Exception{
        ldapService.removeFidoDevice(dev);
    }

    public String generateRandEnrollmentCode(User user) throws Exception{
        String code=UUID.randomUUID().toString();
        ldapService.storeUserEnrollmentCode(user.getRdn(), code);
        return code;
    }

    public void cleanRandEnrollmentCode(User user){

        try{
            ldapService.cleanRandEnrollmentCode(user.getRdn());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }

    }

    public boolean inManagerGroup(User user){

        boolean inManager=false;
        try{
            inManager=ldapService.belongsToManagers(user.getRdn());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return inManager;

    }

}