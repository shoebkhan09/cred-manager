package org.gluu.credmanager.core;

import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.credential.RegisteredCredential;

import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-07-16.
 */
public class User {
    private String userName;
    private String givenName;
    private String email;
    private String rdn;
    private CredentialType preference;
    private boolean admin;

    private Map<CredentialType, List<RegisteredCredential>> credentials;

    public User(){
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setRdn(String rdn) {
        this.rdn = rdn;
    }

    public void setPreference(CredentialType preference) {
        this.preference = preference;
    }

    public void setCredentials(Map<CredentialType, List<RegisteredCredential>> credentials) {
        this.credentials = credentials;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public String getUserName() {
        return userName;
    }

    public String getGivenName() {
        return givenName;
    }

    public String getEmail() {
        return email;
    }

    public String getRdn() {
        return rdn;
    }

    public CredentialType getPreference() {
        return preference;
    }

    public Map<CredentialType, List<RegisteredCredential>> getCredentials() {
        return credentials;
    }

    public boolean isAdmin() {
        return admin;
    }

    /*
    private List<String> mobilePhones;

    public void setMobilePhones(List<String> mobilePhones) {
        this.mobilePhones = mobilePhones;
    }

    public List<String> getMobilePhones() {
        return mobilePhones;
    }

    private String phone;
    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }

     */
}