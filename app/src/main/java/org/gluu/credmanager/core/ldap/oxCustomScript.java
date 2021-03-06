package org.gluu.credmanager.core.ldap;

import com.unboundid.ldap.sdk.ReadOnlyEntry;
import com.unboundid.ldap.sdk.persist.FilterUsage;
import com.unboundid.ldap.sdk.persist.LDAPEntryField;
import com.unboundid.ldap.sdk.persist.LDAPField;
import com.unboundid.ldap.sdk.persist.LDAPObject;
import org.gluu.credmanager.misc.Utils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This class provides an implementation of an object that can be used to
 * represent oxCustomScript objects in the directory.
 * It was generated by the generate-source-from-schema tool provided with the
 * UnboundID LDAP SDK for Java.  It may be customized as desired to better suit
 * your needs.
 */
@LDAPObject(structuralClass="oxCustomScript",
            superiorClass="top")
public class oxCustomScript
{

  // The field to use to hold a read-only copy of the associated entry.
  @LDAPEntryField()
  private ReadOnlyEntry ldapEntry;

  // The field used for RDN attribute inum.
  @LDAPField(attribute="inum",
             objectClass="oxCustomScript",
             inRDN=true,
             filterUsage=FilterUsage.ALWAYS_ALLOWED,
             requiredForEncode=true)
  private String[] inum;

  // The field used for optional attribute description.
  @LDAPField(attribute="description",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] description;

  // The field used for optional attribute displayName.
  @LDAPField(attribute="displayName",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.ALWAYS_ALLOWED)
  private String displayName;

  // The field used for optional attribute gluuStatus.
  @LDAPField(attribute="gluuStatus",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] gluuStatus;

  // The field used for optional attribute oxConfigurationProperty.
  @LDAPField(attribute="oxConfigurationProperty",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxConfigurationProperty;

  // The field used for optional attribute oxLevel.
  @LDAPField(attribute="oxLevel",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxLevel;

  // The field used for optional attribute oxModuleProperty.
  @LDAPField(attribute="oxModuleProperty",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxModuleProperty;

  // The field used for optional attribute oxRevision.
  @LDAPField(attribute="oxRevision",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxRevision;

  // The field used for optional attribute oxScript.
  @LDAPField(attribute="oxScript",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxScript;

  // The field used for optional attribute oxScriptType.
  @LDAPField(attribute="oxScriptType",
             objectClass="oxCustomScript",
             filterUsage=FilterUsage.CONDITIONALLY_ALLOWED)
  private String[] oxScriptType;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        oxCustomScript that = (oxCustomScript) o;
        return Objects.equals(ldapEntry, that.ldapEntry) &&
                Arrays.equals(inum, that.inum) &&
                Arrays.equals(description, that.description) &&
                Objects.equals(displayName, that.displayName) &&
                Arrays.equals(gluuStatus, that.gluuStatus) &&
                Arrays.equals(oxConfigurationProperty, that.oxConfigurationProperty) &&
                Arrays.equals(oxLevel, that.oxLevel) &&
                Arrays.equals(oxModuleProperty, that.oxModuleProperty) &&
                Arrays.equals(oxRevision, that.oxRevision) &&
                Arrays.equals(oxScript, that.oxScript) &&
                Arrays.equals(oxScriptType, that.oxScriptType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ldapEntry, inum, description, displayName, gluuStatus, oxConfigurationProperty, oxLevel,
                oxModuleProperty, oxRevision, oxScript, oxScriptType);
    }


    public String getDisplayName() {
        return displayName;
    }
    /**
     * Retrieves the values for the field associated with the
     * oxConfigurationProperty attribute, if present.
     *
     * @return  The values for the field associated with the
     *          oxConfigurationProperty attribute, or
     *          {@code null} if that attribute was not present in the entry.
     */
    public String[] getConfigurationProperties()
    {
        return oxConfigurationProperty;
    }

    public List<String> getModuleProperties() {
        return Utils.listfromArray(oxModuleProperty);
    }

    /**
     * Retrieves the first value for the field associated with the
     * oxScript attribute, if present.
     *
     * @return  The first value for the field associated with the
     *          oxScript attribute, or
     *          {@code null} if that attribute was not present in the entry or
     *          does not have any values.
     */
    public String getScript()
    {
        if ((oxScript == null) ||
                (oxScript.length == 0))
        {
            return null;
        }
        else
        {
            return oxScript[0];
        }
    }

    /**
     * Retrieves the first value for the field associated with the
     * oxRevision attribute, if present.
     *
     * @return  The first value for the field associated with the
     *          oxRevision attribute, or
     *          {@code null} if that attribute was not present in the entry or
     *          does not have any values.
     */
    public String getRevision()
    {
        if ((oxRevision == null) ||
                (oxRevision.length == 0))
        {
            return null;
        }
        else
        {
            return oxRevision[0];
        }
    }
 /**
   * Sets the value for the field associated with the
   * displayName attribute.
   *
   * @param displayName  The value for the field associated with the
   *            displayName attribute.
   */
  public void setDisplayName(String displayName)
  {
    this.displayName = displayName;
  }

    /**
     * Sets the value for the field associated with the
     * oxRevision attribute.
     *
     * @param revision  The value for the field associated with the
     *            oxRevision attribute.
     */
    public void setRevision(String revision)
    {
        this.oxRevision = new String[] { revision };
    }

}
