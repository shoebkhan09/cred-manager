package org.gluu.credmanager.core.ldap;


import com.unboundid.ldap.sdk.ReadOnlyEntry;
import com.unboundid.ldap.sdk.persist.FilterUsage;
import com.unboundid.ldap.sdk.persist.LDAPEntryField;
import com.unboundid.ldap.sdk.persist.LDAPField;
import com.unboundid.ldap.sdk.persist.LDAPObject;

/**
 * This class provides an implementation of an object that can be used to
 * represent oxAuthConfiguration objects in the directory.
 * It was generated by the generate-source-from-schema tool provided with the
 * UnboundID LDAP SDK for Java.  It may be customized as desired to better suit
 * your needs.
 */
@LDAPObject(structuralClass="oxAuthConfiguration",
            superiorClass="top")
public class oxAuthConfiguration
{

  // The field to use to hold a read-only copy of the associated entry.
  @LDAPEntryField()
  private ReadOnlyEntry ldapEntry;

  // The field used for RDN attribute ou.
  @LDAPField(attribute="ou",
             objectClass="oxAuthConfiguration",
             inRDN=true,
             filterUsage=FilterUsage.ALWAYS_ALLOWED,
             requiredForEncode=true)
  private String[] ou;

  // The field used for optional attribute oxAuthConfDynamic.
  @LDAPField(attribute="oxAuthConfDynamic",
             objectClass="oxAuthConfiguration",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxAuthConfDynamic;

  // The field used for optional attribute oxAuthConfStatic.
  @LDAPField(attribute="oxAuthConfStatic",
             objectClass="oxAuthConfiguration",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxAuthConfStatic;

  /**
   * Retrieves the first value for the field associated with the
   * oxAuthConfDynamic attribute, if present.
   *
   * @return  The first value for the field associated with the
   *          oxAuthConfDynamic attribute, or
   *          {@code null} if that attribute was not present in the entry or
   *          does not have any values.
   */
  public String getAuthConfDynamic()
  {
    if ((oxAuthConfDynamic == null) ||
        (oxAuthConfDynamic.length == 0))
    {
      return null;
    }
    else
    {
      return oxAuthConfDynamic[0];
    }
  }

  /**
   * Retrieves the first value for the field associated with the
   * oxAuthConfStatic attribute, if present.
   *
   * @return  The first value for the field associated with the
   *          oxAuthConfStatic attribute, or
   *          {@code null} if that attribute was not present in the entry or
   *          does not have any values.
   */
  public String getAuthConfStatic()
  {
    if ((oxAuthConfStatic == null) ||
        (oxAuthConfStatic.length == 0))
    {
      return null;
    }
    else
    {
      return oxAuthConfStatic[0];
    }
  }

}