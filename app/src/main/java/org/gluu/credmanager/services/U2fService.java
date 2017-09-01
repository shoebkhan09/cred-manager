package org.gluu.credmanager.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.U2fClientCodes;
import org.gluu.credmanager.conf.jsonized.U2fSettings;
import org.xdi.oxauth.client.fido.u2f.FidoU2fClientFactory;
import org.xdi.oxauth.client.fido.u2f.RegistrationRequestService;
import org.xdi.oxauth.client.fido.u2f.U2fConfigurationService;
import org.xdi.oxauth.model.fido.u2f.U2fConfiguration;
import org.xdi.oxauth.model.fido.u2f.U2fConstants;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterRequest;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterRequestMessage;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterStatus;
import org.zkoss.util.resource.Labels;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jgomer on 2017-08-25.
 * An app. scoped bean that encapsulates logic related to management of registration requests for u2f devices
 */
@ApplicationScoped
public class U2fService {

    @Inject
    AppConfiguration appConfig;

    private Logger logger=LogManager.getLogger(getClass());

    private U2fSettings conf;
    private RegistrationRequestService registrationRequestService;
    private ObjectMapper mapper;

    public U2fService(){}

    /**
     * Triggers a registration request to a U2F endpoint and outputs the request message returned by the service in form of JSON
     * @param userName As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#startRegistration
     * @param sessionState As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#startRegistration
     * @return Json string representation
     * @throws Exception
     */
    public String generateJsonRegisterMessage(String userName, String sessionState) throws Exception{

        U2fConfigurationService u2fCfgServ = FidoU2fClientFactory.instance().createMetaDataConfigurationService(conf.getEndpointUrl());
        U2fConfiguration metadataConf = u2fCfgServ.getMetadataConfiguration();
        registrationRequestService = FidoU2fClientFactory.instance().createRegistrationRequestService(metadataConf);

        //Both oxAuth & cred-manager should have to use the same appId
        RegisterRequestMessage requestMessage=registrationRequestService.startRegistration(userName, conf.getAppId(), sessionState);
        logger.info(Labels.getLabel("app.start_registration_request_start"), requestMessage.getRequestId(), userName, conf.getAppId(), sessionState);

        return getRequestAsJson(requestMessage.getRegisterRequest());

    }

    /**
     * Executes the finish registration step of the U2F service
     * @param userName As required per org.xdi.oxauth.client.fido.u2f.RegistrationRequestService#finishRegistration
     * @param response This is the Json response obtained in the web browser after calling the u2f.register function in Javascript
     * @throws Exception
     */
    public void finishRegistration(String userName, String response) throws Exception{
        //first parameter is not used in current implementation, see: org.xdi.oxauth.ws.rs.fido.u2f.U2fRegistrationWS#finishRegistration
        RegisterStatus status=registrationRequestService.finishRegistration(userName, response);
        logger.info(Labels.getLabel("app.finish_registration_response"), status.getStatus());
    }

    private String getRequestAsJson(RegisterRequest request) throws Exception{

        //This is needed as serialization of RegisterRequestMessage instances behave very weirdly and make Chrome suck more than usual
        Map<String, Object> mainReq = new HashMap<>();
        mainReq.put("challenge", request.getChallenge());
        mainReq.put("appId", request.getAppId());
        mainReq.put("version", U2fConstants.U2F_PROTOCOL_VERSION);

        Map<String, Object> req = new HashMap<>();
        req.put("authenticateRequests", Collections.emptyList());
        req.put("registerRequests", Collections.singletonList(mainReq));

        return mapper.writeValueAsString(req);

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

    @PostConstruct
    public void setup(){
        conf=appConfig.getConfigSettings().getU2fSettings();
        mapper=new ObjectMapper();

    }

}