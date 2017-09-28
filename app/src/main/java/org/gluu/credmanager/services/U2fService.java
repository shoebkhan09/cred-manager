package org.gluu.credmanager.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.utils.URIBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.U2fClientCodes;
import org.gluu.credmanager.conf.jsonized.U2fSettings;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.credential.SecurityKey;
import org.gluu.credmanager.services.ldap.LdapService;
import org.xdi.oxauth.client.fido.u2f.FidoU2fClientFactory;
import org.xdi.oxauth.client.fido.u2f.RegistrationRequestService;
import org.xdi.oxauth.client.fido.u2f.U2fConfigurationService;
import org.xdi.oxauth.model.fido.u2f.U2fConfiguration;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterStatus;
import org.zkoss.util.resource.Labels;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.net.URL;
import java.util.*;

/**
 * Created by jgomer on 2017-08-25.
 * An app. scoped bean that encapsulates logic related to management of registration requests for u2f devices
 */
@ApplicationScoped
public class U2fService {

    @Inject
    AppConfiguration appConfig;

    @Inject
    LdapService ldapService;

    private Logger logger=LogManager.getLogger(getClass());

    private U2fSettings conf;
    private RegistrationRequestService registrationRequestService;
    private ObjectMapper mapper;

    public U2fService(){}

    /**
     * Triggers a registration request to a U2F endpoint and outputs the request message returned by the service in form of JSON
     * @param userName As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#startRegistration
     * @param enrollmentCode A previously generated random code stored under user's LDAP entry
     * @return Json string representation
     * @throws Exception Network problem, De/Serialization error, ...
     */
    public String generateJsonRegisterMessage(String userName, String enrollmentCode) throws Exception{

        //Here we do not use registrationRequestService because this interface unfortunately lacks the enrollment code
        //param, thus we need to interact with the start registration method directly using GET
        JsonNode metadata=mapper.readTree(new URL(conf.getEndpointUrl()));
        URIBuilder uribe = new URIBuilder(metadata.get("registration_endpoint").asText());

        uribe.addParameter("username", userName);
        uribe.addParameter("application", conf.getAppId());     //Both oxAuth & cred-manager should have to use the same appId
        uribe.addParameter("enrollment_code", enrollmentCode);

        //This is needed as serialization of RegisterRequestMessage instances behave very weirdly and make Chrome suck more than usual
        Map<String, Object> request=mapper.readValue(uribe.build().toURL(),new TypeReference<Map<String, Object>>(){});
        request.put("authenticateRequests", Collections.emptyList());

        logger.info(Labels.getLabel("app.start_registration_request_start"), userName, conf.getAppId());

        return mapper.writeValueAsString(request);

    }

    /**
     * Executes the finish registration step of the U2F service
     * @param userName As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#finishRegistration
     * @param response This is the Json response obtained in the web browser after calling the u2f.register function in Javascript
     */
    public void finishRegistration(String userName, String response){
        //first parameter is not used in current implementation, see: org.xdi.oxauth.ws.rs.fido.u2f.U2fRegistrationWS#finishRegistration
        RegisterStatus status=registrationRequestService.finishRegistration(userName, response);
        logger.info(Labels.getLabel("app.finish_registration_response"), status.getStatus());
    }

    public String getRegistrationResult(String jsonString) throws Exception{

        String value=null;
        JsonNode tree=mapper.readTree(jsonString);

        logger.info(Labels.getLabel("app.start_registration_request_result"), jsonString);
        JsonNode tmp=tree.get("errorCode");

        if (tmp!=null)
            try{
                value=U2fClientCodes.get(tmp.asInt()).toString();
                logger.error(Labels.getLabel("app.start_registration_request_error"), value);
                value=value.toLowerCase();
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
                value=Labels.getLabel("general.error.general");
            }
        return value;

    }

    public SecurityKey getLatestSecurityKey(User user, long time){

        SecurityKey sk =null;
        try {
            sk = ldapService.getFidoDevice(user.getRdn(), time, conf.getAppId(), SecurityKey.class);
            if (sk !=null && sk.getNickName()!=null)
                sk =null;    //should have no name
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return sk;

    }

    @PostConstruct
    public void setup(){
        conf=appConfig.getConfigSettings().getU2fSettings();
        mapper=new ObjectMapper();

        U2fConfigurationService u2fCfgServ = FidoU2fClientFactory.instance().createMetaDataConfigurationService(conf.getEndpointUrl());
        U2fConfiguration metadataConf = u2fCfgServ.getMetadataConfiguration();
        registrationRequestService = FidoU2fClientFactory.instance().createRegistrationRequestService(metadataConf);
    }

}