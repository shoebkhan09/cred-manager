/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core.credential;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;

import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-22.
 * The superclass for all type of credentials
 */
public class RegisteredCredential implements Serializable {

    @LdapDN
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String dn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @LdapAttribute(name = "displayName")
    private String nickName;

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickname) {
        this.nickName = nickname;
    }
}
