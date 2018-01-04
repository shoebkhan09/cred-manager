/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.services.ldap.pojo;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapJsonObject;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;
import org.xdi.ldap.model.BaseEntry;
import org.xdi.model.SimpleCustomProperty;

import java.util.List;

/**
 * Created by jgomer on 2017-07-31.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "oxCustomScript"})
public class CustomScript extends BaseEntry {

    @LdapJsonObject
    @LdapAttribute(name = "oxConfigurationProperty")
    private List<SimpleCustomProperty> properties;

    @LdapAttribute(name = "description")
    private String description;

    @LdapAttribute(name = "displayName")
    private String name;

    public CustomScript() {}

    public List<SimpleCustomProperty> getProperties() {
        return properties;
    }

    public void setProperties(List<SimpleCustomProperty> properties) {
        this.properties = properties;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}