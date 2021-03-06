/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core.ldap;

import com.unboundid.ldap.sdk.persist.*;
import org.gluu.credmanager.misc.Utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class provides an implementation of an object that can be used to
 * represent gluuPerson objects in the directory.
 * It was generated by the generate-source-from-schema tool provided with the
 * UnboundID LDAP SDK for Java.  It may be customized as desired to better suit
 * your needs.
 */
@LDAPObject(structuralClass="gluuPerson",
        superiorClass="top")
public class PersonMobile extends BaseLdapPerson {

    // The field used for optional attribute oxMobileDevices.
    @LDAPField(attribute="oxMobileDevices",
            objectClass="gluuPerson",
            filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
    private String[] oxMobileDevices;

    // The field used for optional attribute mobile.
    @LDAPField(attribute="mobile",
            objectClass="gluuPerson",
            filterUsage=FilterUsage.ALWAYS_ALLOWED)
    private String[] mobile;

    /**
     * Retrieves the first value for the field associated with the
     * oxMobileDevices attribute, if present.
     *
     * @return  The first value for the field associated with the
     *          oxMobileDevices attribute, or
     *          {@code null} if that attribute was not present in the entry or
     *          does not have any values.
     */
    public String getMobileDevices()
    {
        if ((oxMobileDevices == null) ||
                (oxMobileDevices.length == 0))
        {
            return null;
        }
        else
        {
            return oxMobileDevices[0];
        }
    }

    /**
     * Sets the values for the field associated with the
     * oxMobileDevices attribute.
     *
     * @param  v  The values for the field associated with the
     *            oxMobileDevices attribute.
     */
    public void setMobileDevices(final String... v)
    {
        this.oxMobileDevices = v;
    }

    /**
     * Retrieves the values for the field associated with the
     * mobile attribute, if present.
     *
     * @return  The values for the field associated with the
     *          mobile attribute, or
     *          {@code null} if that attribute was not present in the entry.
     */
    public String[] getMobile()
    {
        return mobile;
    }

    public List<String> getMobileAsList() {
        if (Utils.isEmpty(mobile)) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(mobile);
        }
    }

    /**
     * Sets the values for the field associated with the
     * oxRevision attribute.
     *
     * @param  v  The values for the field associated with the
     *            oxRevision attribute.
     */
    public void setMobile(final String... v)
    {
        this.mobile = v;
    }

}
