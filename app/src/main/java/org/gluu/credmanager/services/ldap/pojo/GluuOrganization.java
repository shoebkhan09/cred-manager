package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.BaseEntry;
import org.xdi.ldap.model.Entry;

import java.io.Serializable;

/**
 * Created by jgomer on 2017-07-07.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "gluuOrganization"})
public class GluuOrganization extends Entry{

    @LdapAttribute(name = "displayName")
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
