package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;

/**
 * Created by jgomer on 2017-07-09.
 * This class was created instead of reusing org.xdi.config.oxtrust.LdapOxAuthConfiguration because importing oxcore-service
 * project was giving weld problem
 */
@LdapEntry
@LdapObjectClass(values = { "top", "oxAuthConfiguration"})
public class OxAuthConfiguration extends Entry{

    @LdapAttribute(name="oxAuthConfDynamic")
    private String strConfDynamic;

    public String getStrConfDynamic() {
        return strConfDynamic;
    }

    public void setStrConfDynamic(String strConfDynamic) {
        this.strConfDynamic = strConfDynamic;
    }

}
