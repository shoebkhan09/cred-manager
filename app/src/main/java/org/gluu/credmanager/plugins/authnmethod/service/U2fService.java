/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.conf.U2fSettings;
import org.gluu.credmanager.core.ConfigurationHandler;
import org.gluu.credmanager.core.pojo.FidoDevice;
import org.gluu.credmanager.core.pojo.SecurityKey;
import org.gluu.credmanager.plugins.authnmethod.conf.U2FConfig;
import org.slf4j.Logger;
import org.xdi.oxauth.client.fido.u2f.FidoU2fClientFactory;
import org.xdi.oxauth.client.fido.u2f.RegistrationRequestService;
import org.xdi.oxauth.client.fido.u2f.U2fConfigurationService;
import org.xdi.oxauth.model.fido.u2f.U2fConfiguration;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterRequestMessage;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterStatus;
import org.zkoss.util.resource.Labels;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.util.*;
import java.util.stream.Collectors;

/**
 * An app. scoped bean that encapsulates logic related to management of registration requests for u2f devices
 * @author jgomer
 */
@Named
@ApplicationScoped
public class U2fService extends FidoService {

    @Inject
    private Logger logger;

    @Inject
    private MainSettings settings;

    private U2FConfig conf;
    private RegistrationRequestService registrationRequestService;

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
            Map<String, String> props = ldapService.getCustScriptConfigProperties(ConfigurationHandler.DEFAULT_ACR);
            conf.setAppId(props.get("u2f_app_id"));

            logger.info("U2f settings found were: {}", mapper.writeValueAsString(conf));

            U2fConfigurationService u2fCfgServ = FidoU2fClientFactory.instance().createMetaDataConfigurationService(conf.getEndpointUrl());
            U2fConfiguration metadataConf = u2fCfgServ.getMetadataConfiguration();
            registrationRequestService = FidoU2fClientFactory.instance().createRegistrationRequestService(metadataConf);
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

                device.setId(reg.getOxId());
                device.setNickName(reg.getDisplayName());
                device.setCreationDate(reg.getCreationDate());
                device.setLastAccessTime(reg.getLastAccessTime());
                //None of the following should be editable ever:
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

    /**
     * Triggers a registration request to a U2F endpoint and outputs the request message returned by the service in form of JSON
     * @param userName As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#startRegistration
     * @param enrollmentCode A previously generated random code stored under user's LDAP entry
     * @return Json string representation
     * @throws Exception Network problem, De/Serialization error, ...
     */
    public String generateJsonRegisterMessage(String userName, String enrollmentCode) throws Exception {

        RegisterRequestMessage message = registrationRequestService.startRegistration(userName, conf.getAppId(), null, enrollmentCode);

        //This is needed as serialization of RegisterRequestMessage instances behave very weirdly making Chrome suck more than usual
        Map<String, Object> request = mapper.convertValue(message, new TypeReference<Map<String, Object>>() { });
        request.put("authenticateRequests", Collections.emptyList());

        logger.info("Beginning registration start with uid={}, app_id={}", userName, conf.getAppId());
        return mapper.writeValueAsString(request);

    }

    /**
     * Executes the finish registration step of the U2F service
     * @param userName As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#finishRegistration
     * @param response This is the Json response obtained in the web browser after calling the u2f.register function in Javascript
     */
    public void finishRegistration(String userName, String response) {
        //first parameter is not used in current implementation, see: org.xdi.oxauth.ws.rs.fido.u2f.U2fRegistrationWS#finishRegistration
        RegisterStatus status = registrationRequestService.finishRegistration(userName, response);
        logger.info("Response of finish registration: {}", status.getStatus());
    }

    public String getRegistrationResult(String jsonString) throws Exception {

        String value = null;
        JsonNode tree = mapper.readTree(jsonString);

        logger.info("Finished registration start with response: {}", jsonString);
        JsonNode tmp = tree.get("errorCode");

        if (tmp != null) {
            try {
                value = U2fClientCodes.get(tmp.asInt()).toString();
                logger.error("Registration failed with error: {}", value);
                value = value.toLowerCase();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
                value = Labels.getLabel("general.error.general");
            }
        }
        return value;

    }

    public SecurityKey getLatestSecurityKey(String userId, long time) {

        SecurityKey sk = null;
        try {
            sk = getFidoDevice(userId, time, conf.getAppId(), SecurityKey.class);
            if (sk != null && sk.getNickName() != null) {
                sk = null;    //should have no name
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return sk;

    }

}
