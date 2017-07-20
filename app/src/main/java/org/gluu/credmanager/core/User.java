package org.gluu.credmanager.core;

import org.gluu.credmanager.conf.CredentialType;

/**
 * Created by jgomer on 2017-07-16.
 */
public class User {
    private String userName;
    private String givenName;
    private String email;
    private String phone;
    private String mobilePhone;
    private String rdn;
    private CredentialType preference;
    private boolean admin;

    public User(){

    }

    public boolean isAdmin() {
        return admin;
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

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setMobilePhone(String mobilePhone) {
        this.mobilePhone = mobilePhone;
    }

    public void setRdn(String rdn) {
        this.rdn = rdn;
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

    public String getPhone() {
        return phone;
    }

    public String getMobilePhone() {
        return mobilePhone;
    }

    public String getRdn() {
        return rdn;
    }

    public CredentialType getPreference() {
        return preference;
    }

    public void setPreference(CredentialType preference) {
        this.preference = preference;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

}