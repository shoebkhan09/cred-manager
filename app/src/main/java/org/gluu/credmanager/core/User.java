/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.core;

import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.credential.RegisteredCredential;

import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-07-16.
 * An object of this class represents an end-user, contains the most important things such as username, preferred authn
 * method, and set of enrolled credentials. This class is NOT being serialized or annotated to make it persist to LDAP
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

}