/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.core.credential;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapEntry;
import org.gluu.site.ldap.persistence.annotation.LdapObjectClass;

import java.util.Date;

/**
 * Created by jgomer on 2017-09-06.
 * Represents a registered credential of u2f type. It's similar to org.xdi.oxauth.model.fido.u2f.DeviceRegistration but
 * simpler
 */
@LdapEntry
@LdapObjectClass(values = {"top", "oxDeviceRegistration"})
public class FidoDevice extends RegisteredCredential implements Comparable<FidoDevice> {

    @LdapAttribute(name = "creationDate")
    private Date creationDate;

    @LdapAttribute(name = "oxId")
    private String id;

    @LdapAttribute(name = "oxApplication")
    private String application;

    @LdapAttribute(name = "oxCounter")
    private long counter;

    @LdapAttribute(name = "oxDeviceHashCode")
    private int deviceHashCode;

    @LdapAttribute(name = "oxDeviceKeyHandle")
    private String deviceKeyHandle;

    @LdapAttribute(name = "oxDeviceRegistrationConf")
    private String deviceRegistrationConf;

    @LdapAttribute(name = "oxStatus")
    private String status;

    @LdapAttribute(name = "oxLastAccessTime")
    private Date lastAccessTime;

    public Date getLastAccessTime() {
        return lastAccessTime;
    }

    public void setLastAccessTime(Date lastAccessTime) {
        this.lastAccessTime = lastAccessTime;
    }

    public Date getCreationDate() {
        return creationDate;
    }

    public void setCreationDate(Date creationDate) {
        this.creationDate = creationDate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getApplication() {
        return application;
    }

    public void setApplication(String application) {
        this.application = application;
    }

    public long getCounter() {
        return counter;
    }

    public void setCounter(long counter) {
        this.counter = counter;
    }

    public int getDeviceHashCode() {
        return deviceHashCode;
    }

    public void setDeviceHashCode(int deviceHashCode) {
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int compareTo(FidoDevice k){
        long date1=getCreationDate().getTime();
        long date2=k.getCreationDate().getTime();
        return (date1 < date2) ? -1 : ((date1 > date2) ? 1 : 0);
    }

/*
    @LdapAttribute(name = "description")
    private String description;

    @LdapAttribute(name = "oxTrustMetaLastModified")
    private String metaLastModified;

    @LdapAttribute(name = "oxTrustMetaLocation")
    private String metaLocation;

    @LdapAttribute(name = "oxTrustMetaVersion")
    private String metaVersion;

*/
}
