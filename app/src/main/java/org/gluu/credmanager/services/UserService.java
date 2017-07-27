package org.gluu.credmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
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
 */
@ApplicationScoped
public class UserService {

    public static final String[] requiredOpenIdScopes =
            new String[]{"openid","profile","user_name","email","mobile_phone","phone","clientinfo"};

    @Inject
    LdapService ldapService;

    private Logger logger = LogManager.getLogger(getClass());
    private ObjectMapper mapper=new ObjectMapper();

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

    private VerifiedPhone getExtraPhoneInfo(String number, List<VerifiedPhone> list){
        //Complements current phone with extra info in the list if any
        VerifiedPhone phone=new VerifiedPhone(number);
        Optional<VerifiedPhone> extraInfoPhone=list.stream().filter(ph -> number.equals(ph.getNumber())).findFirst();
        if (extraInfoPhone.isPresent()) {
            phone.setLastUsed(extraInfoPhone.get().getLastUsed());
            phone.setNickName(extraInfoPhone.get().getNickName());
        }
        return phone;
    }

    private List<VerifiedPhone> getVerifiedPhones(String rdn){

        try{
            GluuPerson person=ldapService.getGluuPerson(rdn);
            String json=person.getVerifiedPhonesJson();
            Optional<String> optJson= Utils.stringOptional(json);

            if (optJson.isPresent())
                json=mapper.readTree(json).get("phones").toString();
            else
                json="[]";

            List<VerifiedPhone> vphones=mapper.readValue(json, new TypeReference<List<VerifiedPhone>>(){});
logger.debug("Phones from ldap2: {}", vphones);
            Stream<VerifiedPhone> stream=person.getMobileNumbers().stream().map(str-> getExtraPhoneInfo(str, vphones));
            List<VerifiedPhone> list = stream.collect(Collectors.toList());
logger.debug("final phones {} ", list);
            return list;
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
            List<RegisteredCredential> allCreds=new ArrayList<>();
            allCreds.addAll(getVerifiedPhones(rdn));
            allCreds.addAll(getSecurityKeys(rdn));

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