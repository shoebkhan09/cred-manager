package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;
import org.xdi.oxauth.model.fido.u2f.U2fConstants;

import java.util.Collections;
import java.util.List;

/**
 * Created by jgomer on 2017-07-19.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "gluuPerson", "gluuCustomPerson"})
public class GluuPerson extends Entry {

    @LdapAttribute(name = LdapService.PREFERRED_METHOD_ATTR)
    private String preferredAuthMethod;

    @LdapAttribute(name = "oxExternalUid")
    private List<String> externalUids;

    @LdapAttribute(name = LdapService.OTP_DEVICES_ATTR)
    private String otpDevicesJson;

    @LdapAttribute(name= LdapService.MOBILE_PHONE_ATTR)
    private List<String> mobileNumbers;

    @LdapAttribute(name = LdapService.MOBILE_DEVICES_ATTR)
    private String verifiedPhonesJson;

    @LdapAttribute(name = "userPassword")
    private String pass;

    @LdapAttribute(name = U2fConstants.U2F_ENROLLMENT_CODE_ATTRIBUTE)
    private String temporaryEnrollmentCode;

    @LdapAttribute(name = "memberOf")
    private List<String> memberships;

    public List<String> getMemberships() {
        return memberships;
    }

    public void setMemberships(List<String> memberships) {
        this.memberships = memberships;
    }

    public String getTemporaryEnrollmentCode() {
        return temporaryEnrollmentCode;
    }

    public void setTemporaryEnrollmentCode(String temporaryEnrollmentCode) {
        this.temporaryEnrollmentCode = temporaryEnrollmentCode;
    }

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