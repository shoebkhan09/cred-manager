package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.conf.SGConfig;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.credential.SuperGluuDevice;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.SGService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.util.Clients;

import java.util.Date;

/**
 * Created by jgomer on 2017-09-06.
 * This is the ViewModel of page super-detail.zul. It controls the CRUD of supergluu devices
 */
public class UserSuperGluuViewModel extends UserViewModel{

    private static final int QR_SCAN_TIMEOUT=60;

    private Logger logger=LogManager.getLogger(getClass());

    private boolean uiPanelOpened;
    private boolean uiQRShown;
    private boolean uiEnrolled;

    private SGConfig sgConfig;
    private SGService sgService;
    private SuperGluuDevice newDevice;
    private String editingId;

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public boolean isUiQRShown() {
        return uiQRShown;
    }

    public boolean isUiEnrolled() {
        return uiEnrolled;
    }

    public SuperGluuDevice getNewDevice() {
        return newDevice;
    }

    public void setUiPanelOpened(boolean uiPanelOpened) {
        this.uiPanelOpened = uiPanelOpened;
    }

    public void setNewDevice(SuperGluuDevice newDevice) {
        this.newDevice = newDevice;
    }

    public String getEditingId() {
        return editingId;
    }

    public void setEditingId(String editingId) {
        this.editingId = editingId;
    }

    @Init(superclass = true)
    public void childInit() throws Exception{
        devices=user.getCredentials().get(CredentialType.SUPER_GLUU);
        sgService=services.getSgService();
        sgConfig=services.getAppConfig().getConfigSettings().getSgConfig();

        newDevice=new SuperGluuDevice();
        uiPanelOpened=true;
    }

    @Command
    public void showQR(){

        try {
            String code = userService.generateRandEnrollmentCode(user);
            String request = sgService.generateRequest(user.getUserName(), code, WebUtils.getRemoteIP());

            if (request != null){
                uiQRShown = true;
                BindUtils.postNotifyChange(null, null, this, "uiQRShown");

                JavaScriptValue jvalue = new JavaScriptValue(sgConfig.getFormattedQROptions(WebUtils.getPageWidth()));
                //Calls the startQR javascript function supplying suitable params
                Clients.response(new AuInvoke("startQR", request, sgConfig.getLabel(), jvalue, QR_SCAN_TIMEOUT, true));
            }
            else
                showMessageUI(false);
        }
        catch (Exception e){
            showMessageUI(false);
            logger.error(e.getMessage(), e);
        }
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    private void stopPolling(){
        Clients.response(new AuInvoke("stopPolling"));
        uiQRShown = false;
        BindUtils.postNotifyChange(null, null, this, "uiQRShown");
    }

    @Listen("onData=#readyButton")
    public void qrScanResult(Event event) {

        logger.debug("qrScanResult. Event value is {}", event.getData().toString());
        if (uiQRShown) {
            switch (event.getData().toString()) {
                case "timeout":
                    //Indicates progress bar reached 100%
                    stopPolling();
                    break;
                case "poll":
                    newDevice = sgService.getLatestSuperGluuDevice(user, new Date().getTime());
                    if (newDevice != null) {    //New device detected, stop polling
                        stopPolling();
                        try {
                            logger.debug("qrScanResult. Got device {}", newDevice.getId());
                            //It's enrolled in LDAP, nonetheless we are missing the nickname yet and also the check if
                            //it has not previously been enrolled (by another user, for instance)
                            uiEnrolled = sgService.isSGDeviceUnique(newDevice);
                            if (uiEnrolled)
                                BindUtils.postNotifyChange(null, null, this, "uiEnrolled");
                            else {
                                //drop duplicated device from LDAP
                                userService.removeFidoDevice(newDevice);
                                logger.info(Labels.getLabel("app.duplicated_sg_removed"), newDevice.getDeviceData().getUuid());
                                showMessageUI(false, Labels.getLabel("usr.supergluu_already_enrolled"));
                            }
                        }
                        catch (Exception e) {
                            String error=e.getMessage();
                            logger.error(error, e);
                            showMessageUI(false, Labels.getLabel("general.error.detailed", new String[]{error}));
                        }
                    }
                    break;
            }
        }

    }

    @Command
    @NotifyChange({"uiQRShown", "uiPanelOpened", "uiEnrolled", "newDevice", "devices"})
    public void add() {

        if (Utils.stringOptional(newDevice.getNickName()).isPresent()){
            try {
                userService.updateFidoDevice(newDevice);
                devices.add(newDevice);
                showMessageUI(true, Labels.getLabel("usr.enroll.success"));
            }
            catch (Exception e){
                showMessageUI(false, Labels.getLabel("usr.error_updating"));
                logger.error(e.getMessage(), e);
            }
            uiPanelOpened=false;
            resetAddSettings();
        }

    }

    public void resetAddSettings(){
        uiQRShown=false;
        uiEnrolled=false;
        newDevice=new SuperGluuDevice();
    }

    @Command
    @NotifyChange({"uiQRShown", "uiEnrolled", "newDevice"})
    public void cancel() {
        try {
            //Stop tellServer function if still running
            Clients.response(new AuInvoke("stopPolling"));
            //Check if cancelation was made after a real enrollment took place
            if (newDevice!=null && newDevice.getDeviceData()!=null)
                userService.removeFidoDevice(newDevice);
        }
        catch (Exception e){
            showMessageUI(false);
            logger.error(e.getMessage(), e);
        }
        resetAddSettings();

    }

    @NotifyChange({"editingId", "newDevice"})
    @Command
    public void prepareForUpdate(@BindingParam("device") SuperGluuDevice dev){
        //This will make the modal window to become visible
        editingId=dev.getId();
        newDevice=new SuperGluuDevice();
        newDevice.setNickName(dev.getNickName());
    }

    @NotifyChange({"devices", "editingId", "newDevice"})
    @Command
    public void update(){

        String nick=newDevice.getNickName();
        if (Utils.stringOptional(nick).isPresent()) {
            int i=Utils.firstTrue(devices, SuperGluuDevice.class::cast, dev -> dev.getId().equals(editingId));
            SuperGluuDevice dev=(SuperGluuDevice) devices.get(i);
            dev.setNickName(nick);
            cancelUpdate();

            try {
                userService.updateFidoDevice(dev);
                showMessageUI(true);
            }
            catch (Exception e){
                showMessageUI(false);
                logger.error(e.getMessage(), e);
            }
        }

    }

    @NotifyChange({"editingId", "newDevice"})
    @Command
    public void cancelUpdate(){
        newDevice.setNickName(null);
        editingId=null;
    }

    @Command
    public void delete(@BindingParam("device") SuperGluuDevice device){
        processFidoDeviceRemoval(device, this);
    }

}
