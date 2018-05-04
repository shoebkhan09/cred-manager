/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod.service;

import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.conf.U2fSettings;
import org.gluu.credmanager.core.pojo.FidoDevice;
import org.gluu.credmanager.plugins.authnmethod.SecurityKeyExtension;
import org.gluu.credmanager.plugins.authnmethod.conf.U2FConfig;
import org.slf4j.Logger;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * An app. scoped bean that encapsulates logic related to management of registration requests for u2f devices
 * @author jgomer
 */
@ApplicationScoped
public class U2fService extends FidoService {

    @Inject
    private Logger logger;

    @Inject
    private MainSettings settings;

    private U2FConfig conf;

    @PostConstruct
    private void inited() {
        reloadConfiguration();
    }

    public void reloadConfiguration() {

        conf = new U2FConfig();
        String metadataUri = Optional.ofNullable(settings.getU2fSettings()).map(U2fSettings::getRelativeMetadataUri)
                .orElse(".well-known/fido-u2f-configuration");
        conf.setEndpointUrl(String.format("%s/%s", ldapService.getIssuerUrl(), metadataUri));

        try {
            Map<String, String> props = ldapService.getCustScriptConfigProperties(SecurityKeyExtension.ACR);
            conf.setAppId(props.get("u2f_app_id"));

            logger.info("U2f settings found were: {}", mapper.writeValueAsString(conf));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    public int getDevicesTotal(String userId, boolean active) {
        return getDevicesTotal(conf.getAppId(), userId, active);
    }

    public List<FidoDevice> getDevices(String userId, boolean active) {

        List<FidoDevice> devices = new ArrayList<>();
        try {
            devices = getRegistrations(conf.getAppId(), userId, active).stream().map(reg -> {
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
