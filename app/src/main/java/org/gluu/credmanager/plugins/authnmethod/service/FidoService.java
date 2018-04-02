/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod.service;

import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.core.ldap.oxDeviceRegistration;
import org.slf4j.Logger;
import org.xdi.oxauth.model.fido.u2f.DeviceRegistrationStatus;

import javax.inject.Inject;
import java.util.List;

/**
 * @author jgomer
 */
public class FidoService extends BaseService {

    @Inject
    private Logger logger;

    @Inject
    MainSettings settings;

    public int getDevicesTotal(String appId, String userId, boolean active) {

        int total = 0;
        try {
            total = getRegistrations(appId, userId, active).size();
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return total;

    }

    List<oxDeviceRegistration> getRegistrations(String appId, String userId, boolean active) {

        String parentDn = String.format("ou=fido,%s", ldapService.getPersonDn(userId));

        oxDeviceRegistration deviceRegistration = new oxDeviceRegistration();
        deviceRegistration.setOxApplication(appId);
        deviceRegistration.setOxStatus(active ? DeviceRegistrationStatus.ACTIVE.getValue() : DeviceRegistrationStatus.COMPROMISED.getValue());

        return ldapService.find(deviceRegistration, oxDeviceRegistration.class, parentDn);

    }

}
