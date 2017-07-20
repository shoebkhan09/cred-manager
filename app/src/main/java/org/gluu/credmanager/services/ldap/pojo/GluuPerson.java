package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;

import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-19.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "gluuPerson", "gluuCustomPerson"})
public class GluuPerson implements Serializable {

    @LdapDN
    private String dn;

    //TODO: use another LDAP attribute!
    @LdapAttribute(name = "preferredDeliveryMethod")
    private String preferredAuthMethod;

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getPreferredAuthMethod() {
        return preferredAuthMethod;
    }

    public void setPreferredAuthMethod(String preferredAuthMethod) {
        this.preferredAuthMethod = preferredAuthMethod;
    }

}
