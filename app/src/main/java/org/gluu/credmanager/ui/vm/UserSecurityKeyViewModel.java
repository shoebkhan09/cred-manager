package org.gluu.credmanager.ui.vm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.U2fClientCodes;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.services.ServiceMashup;
import org.xdi.oxauth.client.fido.u2f.FidoU2fClientFactory;
import org.xdi.oxauth.client.fido.u2f.RegistrationRequestService;
import org.xdi.oxauth.client.fido.u2f.U2fConfigurationService;
import org.xdi.oxauth.model.fido.u2f.U2fConfiguration;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterRequestMessage;
import org.xdi.oxauth.model.fido.u2f.protocol.RegisterStatus;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.util.Clients;

/**
 * Created by jgomer on 2017-07-23.
 */
public class UserSecurityKeyViewModel extends BasicViewModel {

    private ServiceMashup services;
    private User user;
    private String sessionState;
    private boolean uiAwaiting;
    private boolean uiEnrolled;
    private boolean uiPanelOpened=true;
    private String nickName;

    private RegistrationRequestService registrationRequestService;
    private Logger logger = LogManager.getLogger(getClass());
    private ObjectMapper mapper=new ObjectMapper();

    private static final int REGISTRATION_TIMEOUT=10;

    public boolean isUiAwaiting() {
        return uiAwaiting;
    }

    public boolean isUiEnrolled() {
        return uiEnrolled;
    }

    public String getNickName() {
        return nickName;
    }

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    @Init(superclass = true)
    public void childInit() throws Exception{

        Session se= Sessions.getCurrent();
        services= WebUtils.getServices(se);
        this.user=WebUtils.getUser(se);

        sessionState=WebUtils.getCookie("session_state");
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    //@NotifyChange( {"uiAwaiting", "uiEnrolled"} )
    @Listen("onData=#readyButton")
    public void submit(Event event) throws Exception{

        String JsonStr=mapper.writeValueAsString(event.getData());
        JsonNode tree=mapper.readTree(JsonStr);

        logger.info(Labels.getLabel("app.start_registration_request_result"), JsonStr);

        JsonNode tmp=tree.get("errorCode");
        if(tmp==null){
            //first parameter is not used in current implementation, see: org.xdi.oxauth.ws.rs.fido.u2f.U2fRegistrationWS#finishRegistration
            RegisterStatus status=registrationRequestService.finishRegistration(null, JsonStr);
            logger.info(Labels.getLabel("app.finish_registration_response"), status.getStatus());
            uiEnrolled=true;
        }
        else
            logger.error(Labels.getLabel("app.start_registration_request_error"), U2fClientCodes.get(tmp.asInt()).toString());

        uiAwaiting=false;
        BindUtils.postNotifyChange(null,	null, this, "uiAwaiting");
        BindUtils.postNotifyChange(null,	null, this, "uiEnrolled");
    }

    //@NotifyChange("uiAwaiting")
    @Command
    public void triggerU2fRegisterRequest(){

        try {
            uiAwaiting =true;
            BindUtils.postNotifyChange(null,	null, this, "uiAwaiting");

            String appId = services.getAppConfig().getConfigSettings().getOxdConfig().getRedirectUri();
            U2fConfigurationService u2fCfgServ = FidoU2fClientFactory.instance().createMetaDataConfigurationService(services.getAppConfig().getU2fMetadataUri());
            U2fConfiguration metadataConf = u2fCfgServ.getMetadataConfiguration();

            registrationRequestService = FidoU2fClientFactory.instance().createRegistrationRequestService(metadataConf);
            //RegisterRequestMessage requestMessage=registrationRequestService.startRegistration(user.getUserName(), appId, sessionState);
            RegisterRequestMessage requestMessage = registrationRequestService.startRegistration(null, appId, sessionState);

            logger.info(Labels.getLabel("app.start_registration_request_start"), requestMessage.getRequestId(), null, appId, null);
            //logger.info(Labels.getLabel("app.registration_request"), requestMessage.getRequestId(), user.getUserName(), appId, sessionState);
            String JsonRequest=mapper.writeValueAsString(requestMessage);

            //Notify browser to exec proper function
            Clients.response(new AuInvoke("triggerU2fRegistration", new JavaScriptValue(JsonRequest), REGISTRATION_TIMEOUT));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

    @NotifyChange( "uiPanelOpened" )
    @Command
    public void addKey(){
        //TODO: edit current reg. with nickname
    }

    @NotifyChange( {"uiPanelOpened", "nickName"} )
    @Command
    public void cancelAddKey(){
        //TODO: undo enrolled key
        nickName=null;
        uiPanelOpened=false;
    }

}
