package org.gluu.credmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.credential.OTPDevice;
import org.gluu.credmanager.core.credential.SecurityKey;
import org.gluu.credmanager.core.credential.VerifiedPhone;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.ldap.pojo.GluuPerson;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.conf.CredentialType;
import static org.gluu.credmanager.conf.CredentialType.OTP;
import static org.gluu.credmanager.conf.CredentialType.VERIFIED_PHONE;
import static org.gluu.credmanager.conf.CredentialType.SECURITY_KEY;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-07-16.
 * An app. scoped bean that encapsulates logic related to users manipulation (CRUD) at memory level (no LDAP storage)
 */
@ApplicationScoped
public class UserService {

    /*
    The list of OpenId scopes required to be able to inspect the claims needed. See attributes of User class
     */
    public static final String[] requiredOpenIdScopes =
            new String[]{"openid","profile","user_name","email","mobile_phone","phone","clientinfo"};

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
        u.setGivenName(getClaim(claims,"given_name"));
        u.setEmail(getClaim(claims,"email"));
        //u.setPhone(getClaim(claims, "phone_number_verified"));
        //u.setMobilePhones(claims.get("phone_mobile_number"));

        String inum=getClaim(claims, "inum");
        if (inum!=null)
            u.setRdn("inum=" + inum);

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
logger.debug("Hashed id {}", hash);

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
logger.debug("Phones from ldap2: {}", vphones);
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

    private List<SecurityKey> getSecurityKeys(String rdn){

        try{
            ldapService.createFidoBranch(rdn);
            return ldapService.getFidoDevices(rdn);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    public Map<CredentialType, List<RegisteredCredential>> getPersonalMethods(User user){

        try {
            String rdn = user.getRdn();
            GluuPerson person=ldapService.getGluuPerson(rdn);
            Utils.emptyNullLists(person, ArrayList::new);

            Map<CredentialType, List<RegisteredCredential>> allCreds=new HashMap<>();
            allCreds.put(VERIFIED_PHONE, Utils.mapSortCollectList(getVerifiedPhones(person), RegisteredCredential.class::cast));
            allCreds.put(SECURITY_KEY, Utils.mapSortCollectList(getSecurityKeys(rdn), RegisteredCredential.class::cast));
            allCreds.put(OTP, Utils.mapSortCollectList(getOtpDevices(person), RegisteredCredential.class::cast));
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
     * @param enabledMethods Current set of enabled authentication methods
     * @return The set referred above.
     */
    public Set<CredentialType> getEffectiveMethods(User user, Set<CredentialType> enabledMethods){
        //Get those credentials types that have associated at least one item
        Stream<Map.Entry<CredentialType, List<RegisteredCredential>>> stream=user.getCredentials().entrySet().stream();
        Set<CredentialType> set=stream.filter(e -> e.getValue().size()>0).map(e -> e.getKey()).collect(Collectors.toCollection(HashSet::new));
        set.retainAll(enabledMethods);
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

    public SecurityKey relocateFidoDevice(User user, long time) throws Exception{
        return ldapService.relocateFidoDevice(user.getRdn(), time);
    }

    public void updateU2fDevice(SecurityKey key) throws Exception{
        ldapService.updateU2fDevice(key);
    }

    public void removeU2fDevice(SecurityKey key) throws Exception{
        ldapService.removeU2fDevice(key);
    }

}