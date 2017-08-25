package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;

import java.util.Collections;
import java.util.List;

/**
 * Created by jgomer on 2017-07-19.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "gluuPerson", "gluuCustomPerson"})
public class GluuPerson extends Entry {

    @LdapAttribute(name="mobile")
    private List<String> mobileNumbers;

    @LdapAttribute(name = "oxExternalUid")
    private List<String> externalUids;

    //TODO: use another LDAP attributes
    @LdapAttribute(name = "description")
    private String preferredAuthMethod;

    @LdapAttribute(name = "persistentId")
    private String otpDevicesJson;

    @LdapAttribute(name = "transientId")
    private String verifiedPhonesJson;

    @LdapAttribute(name = "userPassword")
    private String pass;

    public String getPass() {
        return pass;
    }

    public void setPass(String pass) {
        this.pass = pass;
    }

    public String getPreferredAuthMethod() {
        return preferredAuthMethod;
    }

    public void setPreferredAuthMethod(String preferredAuthMethod) {
        this.preferredAuthMethod = preferredAuthMethod;
    }

    public List<String> getMobileNumbers() {
        return mobileNumbers;
    }

    public void setMobileNumbers(List<String> mobileNumbers) {
        this.mobileNumbers = mobileNumbers;
    }

    public String getVerifiedPhonesJson() {
        return verifiedPhonesJson;
    }

    public void setVerifiedPhonesJson(String verifiedPhonesJson) {
        this.verifiedPhonesJson = verifiedPhonesJson;
    }

    public String getOtpDevicesJson() {
        return otpDevicesJson;
    }

    public void setOtpDevicesJson(String otpDevicesJson) {
        this.otpDevicesJson = otpDevicesJson;
    }

    public List<String> getExternalUids() {
        return externalUids;
    }

    public void setExternalUids(List<String> externalUids) {
        this.externalUids = externalUids;
    }
}