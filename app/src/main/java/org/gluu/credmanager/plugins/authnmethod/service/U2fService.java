/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod.service;

import org.gluu.credmanager.core.pojo.FidoDevice;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * An app. scoped bean that encapsulates logic related to management of registration requests for u2f devices
 * @author jgomer
 */
@ApplicationScoped
public class U2fService extends FidoService {

    @Inject
    private Logger logger;

    private String appId;

    @PostConstruct
    private void inited() {
        appId = settings.getU2fSettings().getAppId();
    }

    public int getDevicesTotal(String userId, boolean active) {
        return getDevicesTotal(appId, userId, active);
    }

    public List<FidoDevice> getDevices(String userId, boolean active) {

        List<FidoDevice> devices = new ArrayList<>();
        try {
            devices = getRegistrations(appId, userId, active).stream().map(reg -> {
                FidoDevice device = new FidoDevice();

                device.setCreationDate(reg.getCreationDate());
                device.setId(reg.getOxId());
                device.setNickName(reg.getDisplayName());
                //device.setApplication(appId);
                //device.setCounter(reg.getOxCounter());
                //device.setStatus(reg.getOxStatus());

                return device;
            }).sorted().collect(Collectors.toList());

            logger.trace("getDevices. User '{}' has {}", userId, devices.stream().map(FidoDevice::getId).collect(Collectors.toList()));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return devices;

    }

}
