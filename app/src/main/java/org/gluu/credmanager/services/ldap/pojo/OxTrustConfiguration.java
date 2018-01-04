/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.Entry;

/**
 * Created by jgomer on 2017-07-09.
 * This class was created instead of reusing org.xdi.config.oxtrust.LdapOxTrustConfiguration because importing oxcore-service
 * project was giving weld problem
 */
@LdapEntry
@LdapObjectClass(values = { "top", "oxTrustConfiguration"})
public class OxTrustConfiguration extends Entry{

    @LdapAttribute(name="oxTrustConfCacheRefresh")
    private String confCacheRefreshStr;

    @LdapAttribute(name="oxTrustConfApplication")
    private String confApplicationStr;

    public String getConfCacheRefreshStr() {
        return confCacheRefreshStr;
    }

    public void setConfCacheRefreshStr(String confCacheRefreshStr) {
        this.confCacheRefreshStr = confCacheRefreshStr;
    }

    public String getConfApplicationStr() {
        return confApplicationStr;
    }

    public void setConfApplicationStr(String confApplicationStr) {
        this.confApplicationStr = confApplicationStr;
    }

}