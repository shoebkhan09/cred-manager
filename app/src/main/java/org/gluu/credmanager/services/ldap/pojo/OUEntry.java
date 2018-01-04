/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;

/**
 * Created by jgomer on 2017-08-11.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "organizationalUnit"})
public class OUEntry extends Entry {

    @LdapAttribute(name = "ou")
    private String ou;

    public String getOu() {
        return ou;
    }

    public void setOu(String ou) {
        this.ou = ou;
    }

}