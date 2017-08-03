package org.gluu.credmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.credential.OTPDevice;
import org.gluu.credmanager.core.credential.SecurityKey;
import org.gluu.credmanager.core.credential.VerifiedPhone;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.ldap.pojo.GluuPerson;
import org.gluu.credmanager.core.credential.RegisteredCredential;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

        try {
            GluuPerson person = ldapService.getGluuPerson(user.getRdn());
            return CredentialType.get(person.getPreferredAuthMethod());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return null;
        }

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

        uid=uid.replaceFirst("hotp:","").replaceFirst("totp:","");
        int idx=uid.indexOf(";");
        if (idx>0)
            uid=uid.substring(0,idx);
        int hash=uid.hashCode();

        OTPDevice device=new OTPDevice(hash);
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
            return ldapService.getFidoDevices(rdn);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    //TODO: implement otp & supergluu
    public List<RegisteredCredential> getPersonalMethods(User user){

        try {
            String rdn = user.getRdn();
            GluuPerson person=ldapService.getGluuPerson(rdn);

            List<RegisteredCredential> allCreds=new ArrayList<>();
            allCreds.addAll(getVerifiedPhones(person));
            allCreds.addAll(getSecurityKeys(rdn));
            allCreds.addAll(getOtpDevices(person));

            return allCreds;
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return null;
        }
    }

    public void updateMobilePhones(User user, List<VerifiedPhone> mobiles, VerifiedPhone newPhone) throws Exception{

        //See getVerifiedPhones() above
        List<VerifiedPhone> vphones=new ArrayList<>(mobiles);
        vphones.add(newPhone);

        String json=String.format("{%sphones%s: %s}", "\"", "\"", mapper.writeValueAsString(vphones));

        ldapService.updateMobilePhones(user.getRdn(), mobiles, newPhone, json);
        //modify list only if LDAP update took place
        mobiles.add(newPhone);

    }

}