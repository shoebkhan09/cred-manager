package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;

import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-07.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "gluuOrganization"})
public class GluuOrganization implements Serializable{

    @LdapDN
    private String dn;

    @LdapAttribute(name = "displayName")
    private String name;

    public String getName() {
        return name;
    }

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public void setName(String name) {
        this.name = name;
    }

}
