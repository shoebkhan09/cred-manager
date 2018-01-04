/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.core.credential;

import org.gluu.site.ldap.persistence.annotation.LdapAttribute;
import org.gluu.site.ldap.persistence.annotation.LdapJsonObject;
import org.xdi.oxauth.model.fido.u2f.protocol.DeviceData;

/**
 * Created by jgomer on 2017-09-06.
 * Represents a registered credential corresponding to a supergluu device
 */
public class SuperGluuDevice extends FidoDevice {

    public SuperGluuDevice(){}

    @LdapJsonObject
    @LdapAttribute(name = "oxDeviceData")
    private DeviceData deviceData;

    public DeviceData getDeviceData() {
        return deviceData;
    }

    public void setDeviceData(DeviceData deviceData) {
        this.deviceData = deviceData;
    }

}
