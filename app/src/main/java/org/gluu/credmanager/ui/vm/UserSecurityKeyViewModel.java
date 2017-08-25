package org.gluu.credmanager.ui.vm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.credential.SecurityKey;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.U2fService;
import org.gluu.credmanager.services.UserService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Messagebox;

import static org.gluu.credmanager.conf.CredentialType.SECURITY_KEY;

import java.util.*;

/**
 * Created by jgomer on 2017-07-23.
 */
public class UserSecurityKeyViewModel extends UserViewModel{

    private static final int REGISTRATION_TIMEOUT=8000;
    private static final int WAIT_ENROLL_TIME=1000;
    private Logger logger=LogManager.getLogger(getClass());
    private ObjectMapper mapper;

    private boolean uiAwaiting;
    private boolean uiEnrolled;
    private boolean uiPanelOpened;

    //private String sessionState;
    private SecurityKey newDevice;
    private U2fService u2fService;
    private UserService usrService;

    private String editingId;

    public boolean isUiAwaiting() {
        return uiAwaiting;
    }

    public boolean isUiEnrolled() {
        return uiEnrolled;
    }

    public SecurityKey getNewDevice() {
        return newDevice;
    }

    public String getEditingId() {
        return editingId;
    }

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public void setEditingId(String editingId) {
        this.editingId = editingId;
    }

    public void setNewDevice(SecurityKey newDevice) {
        this.newDevice = newDevice;
    }

    @Init(superclass = true)
    public void childInit() throws Exception{
        //sessionState=WebUtils.getCookie("session_state");
        mapper=new ObjectMapper();

        devices=user.getCredentials().get(CredentialType.SECURITY_KEY);
        newDevice=new SecurityKey();
        uiPanelOpened=true;
        u2fService=services.getU2fService();
        usrService = services.getUserService();
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    @Command
    public void triggerU2fRegisterRequest(){
        try {
            uiAwaiting =true;
            BindUtils.postNotifyChange(null,	null, this, "uiAwaiting");

            //String JsonRequest=u2fService.getJsonRegisterMessage(user.getUserName(), sessionState);
            String JsonRequest=u2fService.generateJsonRegisterMessage(null, null);

            //Notify browser to exec proper function
            Clients.showNotification(Labels.getLabel("usr.u2f_touch"), Clients.NOTIFICATION_TYPE_INFO, null, "middle_center", WAIT_ENROLL_TIME);
            Clients.response(new AuInvoke ("triggerU2fRegistration", new JavaScriptValue(JsonRequest), REGISTRATION_TIMEOUT));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }

    }

    @Listen("onData=#readyButton")
    public void notified(Event event) throws Exception{

        String JsonStr=mapper.writeValueAsString(event.getData());
        String error=u2fService.getRegistrationResult(JsonStr);

        if(error==null){
            try {
                u2fService.finishRegistration(null, JsonStr);
                newDevice=usrService.relocateFidoDevice(user, new Date().getTime());

                uiEnrolled=true;
                BindUtils.postNotifyChange(null,	null, this, "uiEnrolled");
            }
            catch (Exception e){
                showMessageUI(false);
                logger.error(Labels.getLabel("app.finish_registration_error"), e);
            }
        }
        else
            showMessageUI(false, Labels.getLabel("general.error.detailed", new String[]{error}));

        uiAwaiting=false;
        BindUtils.postNotifyChange(null,	null, this, "uiAwaiting");

    }

    @NotifyChange( {"uiPanelOpened", "uiEnrolled", "newDevice", "devices"} )
    @Command
    public void add(){

        uiPanelOpened=false;
        try {
            usrService.updateU2fDevice(newDevice);
logger.debug("device {}", newDevice.getNickName());
            devices.add(newDevice);
            resetAddSettings();
            showMessageUI(true, Labels.getLabel("usr.enroll.success"));
        }
        catch (Exception e){
            showMessageUI(false, Labels.getLabel("usr.error_updating"));
            logger.error(e.getMessage(), e);
        }

    }

    public void resetAddSettings(){
        uiEnrolled=false;
        newDevice=new SecurityKey();
    }

    @NotifyChange( {"uiEnrolled", "newDevice"} )
    @Command
    public void cancel(){

        try {
            //remove recently enrolled key
            usrService.removeU2fDevice(newDevice);
        }
        catch (Exception e){
            showMessageUI(false);
            logger.error(e.getMessage(), e);
        }
        resetAddSettings();

    }

    @NotifyChange({"editingId", "newDevice"})
    @Command
    public void prepareForUpdate(@BindingParam("device") SecurityKey dev){
        //This will make the modal window to become visible
        editingId=dev.getId();
        newDevice=new SecurityKey();
        newDevice.setNickName(dev.getNickName());
    }

    @NotifyChange({"editingId", "newDevice"})
    @Command
    public void cancelUpdate(){
        newDevice.setNickName(null);
        editingId=null;
    }

    @NotifyChange({"devices", "editingId", "newDevice"})
    @Command
    public void update(){

        String nick=newDevice.getNickName();
        if (nick!=null){
            int i= Utils.firstTrue(devices, SecurityKey.class::cast, dev -> dev.getId().equals(editingId));
            SecurityKey dev=(SecurityKey) devices.get(i);
            dev.setNickName(nick);
            cancelUpdate();

            try {
                usrService.updateU2fDevice(dev);
                showMessageUI(true);
            }
            catch (Exception e){
                showMessageUI(false);
                logger.error(e.getMessage(), e);
            }
        }

    }

    @Command
    public void delete(@BindingParam("device") SecurityKey device){

        boolean flag=mayTriggerResetPreference(user.getPreference(), devices, SECURITY_KEY);
        Pair<String, String> delMessages=getDelMessages(flag, SECURITY_KEY, device.getNickName());

        Messagebox.show(delMessages.getY(), delMessages.getX(), Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                event -> {
                    if (Messagebox.ON_YES.equals(event.getName())) {
                        try {
                            if (devices.remove(device)) {
                                if (flag)
                                    usrService.setPreferredMethod(user,null);

                                usrService.removeU2fDevice(device);
                                //trigger refresh (this method is asynchronous...)
                                BindUtils.postNotifyChange(null, null, UserSecurityKeyViewModel.this, "devices");
                                showMessageUI(true);
                            }
                        } catch (Exception e) {
                            showMessageUI(false);
                            logger.error(e.getMessage(), e);
                        }
                    }
                });
    }

}
