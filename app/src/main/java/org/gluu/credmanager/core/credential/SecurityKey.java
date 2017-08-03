package org.gluu.credmanager.core.credential;

import org.gluu.credmanager.conf.CredentialType;
import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;

/**
 * Created by jgomer on 2017-07-25.
 */
@LdapEntry
@LdapObjectClass(values = {"top", "oxDeviceRegistration"})
public class SecurityKey extends RegisteredCredential {

    public SecurityKey(){
        setType(CredentialType.SECURITY_KEY);
    }

    /*

    @LdapAttribute(name = "oxId", ignoreDuringUpdate = true)
    private String id;

    @LdapAttribute(name = "creationDate", ignoreDuringUpdate = true)
    private String creationDate;

    @LdapAttribute(name = "oxApplication", ignoreDuringUpdate = true)
    private String application;

    @LdapAttribute(name = "oxCounter", ignoreDuringUpdate = true)
    private String counter;

    @LdapAttribute(name = "oxDeviceData", ignoreDuringUpdate = true)
    private String deviceData;

    @LdapAttribute(name = "oxDeviceHashCode", ignoreDuringUpdate = true)
    private String deviceHashCode;

    @LdapAttribute(name = "oxDeviceKeyHandle", ignoreDuringUpdate = true)
    private String deviceKeyHandle;

    @LdapAttribute(name = "oxDeviceRegistrationConf", ignoreDuringUpdate = true)
    private String deviceRegistrationConf;

    @LdapAttribute(name = "oxLastAccessTime", ignoreDuringUpdate = true)
    private String lastAccessTime;

    @LdapAttribute(name = "oxStatus", ignoreDuringUpdate = true)
    private String status;

    @LdapAttribute(name = "displayName")
    private String displayName;

    @LdapAttribute(name = "description")
    private String description;

    @LdapAttribute(name = "oxTrustMetaLastModified")
    private String metaLastModified;

    @LdapAttribute(name = "oxTrustMetaLocation")
    private String metaLocation;

    @LdapAttribute(name = "oxTrustMetaVersion")
    private String metaVersion;

    public String getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(String creationDate) {
        this.creationDate = creationDate;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public String getCounter() {
        return counter;
    }

    public void setCounter(String counter) {
        this.counter = counter;
    }

    public String getDeviceData() {
        return deviceData;
    }

    public void setDeviceData(String deviceData) {
        this.deviceData = deviceData;
    }

    public String getDeviceHashCode() {
        return deviceHashCode;
    }

    public void setDeviceHashCode(String deviceHashCode) {
        this.deviceHashCode = deviceHashCode;
    }

    public String getDeviceKeyHandle() {
        return deviceKeyHandle;
    }

    public void setDeviceKeyHandle(String deviceKeyHandle) {
        this.deviceKeyHandle = deviceKeyHandle;
    }

    public String getDeviceRegistrationConf() {
        return deviceRegistrationConf;
    }

    public void setDeviceRegistrationConf(String deviceRegistrationConf) {
        this.deviceRegistrationConf = deviceRegistrationConf;
    }

    public String getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(String lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getMetaLastModified() {
        return metaLastModified;
    }

    public void setMetaLastModified(String metaLastModified) {
        this.metaLastModified = metaLastModified;
    }

    public String getMetaLocation() {
        return metaLocation;
    }

    public void setMetaLocation(String metaLocation) {
        this.metaLocation = metaLocation;
    }

    public String getMetaVersion() {
        return metaVersion;
    }

    public void setMetaVersion(String metaVersion) {
        this.metaVersion = metaVersion;
    }
*/

}