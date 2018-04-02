package org.gluu.credmanager.core.ldap;

import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.ReadOnlyEntry;
import com.unboundid.ldap.sdk.persist.FilterUsage;
import com.unboundid.ldap.sdk.persist.LDAPEntryField;
import com.unboundid.ldap.sdk.persist.LDAPField;
import com.unboundid.ldap.sdk.persist.LDAPObject;
import org.gluu.credmanager.misc.Utils;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * This class provides an implementation of an object that can be used to
 * represent gluuPerson objects in the directory.
 */
@LDAPObject(structuralClass="gluuPerson",
        superiorClass="top")
public class gluuPersonMember {

    // The field to use to hold a read-only copy of the associated entry.
    @LDAPEntryField()
    private ReadOnlyEntry ldapEntry;

    // The field used for RDN attribute ou.
    @LDAPField(attribute="inum",
            objectClass="gluuPerson",
            inRDN=true,
            filterUsage= FilterUsage.ALWAYS_ALLOWED,
            requiredForEncode=true)
    private String[] inum;

    // The field used for optional attribute gluuManagerGroup.
    @LDAPField(attribute="memberOf",
            objectClass="gluuPerson",
            filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
    private DN[] memberOf;

    /**
     * Retrieves the first value for the field associated with the
     * memberOf attribute as a DN, if present.
     *
     * @return  The first value for the field associated with the
     *          memberOf attribute, or
     *          {@code null} if that attribute was not present in the entry or
     *          does not have any values.
     */
    public DN getFirstMemberOfDN()
    {
        if ((memberOf == null) ||
                (memberOf.length == 0))
        {
            return null;
        }
        else
        {
            return memberOf[0];
        }
    }



    /**
     * Retrieves the values for the field associated with the
     * memberOf attribute as DNs, if present.
     *
     * @return  The values for the field associated with the
     *          memberOf attribute, or
     *          {@code null} if that attribute was not present in the entry.
     */
    public DN[] getMemberOfDNs()
    {
        return memberOf;
    }

    public List<DN> getMemberOfDNsAsList() {
        if (Utils.isEmpty(memberOf)) {
            return Collections.emptyList();
        } else {
            return Arrays.asList(memberOf);
        }
    }

}
