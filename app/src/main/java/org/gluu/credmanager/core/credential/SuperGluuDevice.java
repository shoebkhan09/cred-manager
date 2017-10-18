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
