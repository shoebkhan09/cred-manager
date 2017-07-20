package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;

/**
 * Created by jgomer on 2017-07-09.
 */
@LdapEntry
@LdapObjectClass(values = { "top", "oxAuthConfiguration"})
public class OxAuthConfiguration {

    @LdapDN
    private String dn;

    @LdapAttribute(name="oxAuthConfDynamic")
    private String strConfDynamic;

    public String getDn() {
        return dn;
    }

    public void setDn(String dn) {
        this.dn = dn;
    }

    public String getStrConfDynamic() {
        return strConfDynamic;
    }

    public void setStrConfDynamic(String strConfDynamic) {
        this.strConfDynamic = strConfDynamic;
    }

}
