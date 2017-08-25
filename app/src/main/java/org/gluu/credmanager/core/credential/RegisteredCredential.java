package org.gluu.credmanager.core.credential;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;

import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-22.
 * Similar but not equal to org.xdi.oxauth.model.fido.u2f.DeviceRegistration and org.gluu.oxtrust.model.fido.GluuCustomFidoDevice
 */
public class RegisteredCredential  implements Serializable {

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
