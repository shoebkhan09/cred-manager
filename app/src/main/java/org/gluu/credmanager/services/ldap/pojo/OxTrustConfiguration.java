package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapDN;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;

/**
 * Created by jgomer on 2017-07-09.
 */
@LdapEntry
@LdapObjectClass(values = { "top", "oxTrustConfiguration"})
public class OxTrustConfiguration extends Entry{

    @LdapAttribute(name="oxTrustConfCacheRefresh")
    private String strConfCacheRefresh;

    @LdapAttribute(name="oxTrustConfApplication")
    private String strConfApplication;

    public String getStrConfCacheRefresh() {
        return strConfCacheRefresh;
    }

    public void setStrConfCacheRefresh(String strConfCacheRefresh) {
        this.strConfCacheRefresh = strConfCacheRefresh;
    }

    public String getStrConfApplication() {
        return strConfApplication;
    }

    public void setStrConfApplication(String strConfApplication) {
        this.strConfApplication = strConfApplication;
    }

}