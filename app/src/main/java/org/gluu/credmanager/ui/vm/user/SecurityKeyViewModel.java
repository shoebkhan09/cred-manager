/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.pojo.FidoDevice;
import org.gluu.credmanager.core.pojo.SecurityKey;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.plugins.authnmethod.service.U2fService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.util.Date;
import java.util.List;

/**
 * Created by jgomer on 2017-07-23.
 * This is the ViewModel of page u2f-detail.zul. It controls the CRUD of security keys
 */
@VariableResolver(DelegatingVariableResolver.class)
public class SecurityKeyViewModel extends UserViewModel {

    private static final int REGISTRATION_TIMEOUT = 8000;
    private static final int WAIT_ENROLL_TIME = 1000;

    private Logger logger = LogManager.getLogger(getClass());

    @WireVariable
    private U2fService u2fService;

    private ObjectMapper mapper;
    private SecurityKey newDevice;
    private List<FidoDevice> devices;

    private boolean uiAwaiting;
    private boolean uiEnrolled;
    private boolean uiPanelOpened;
    private String editingId;

    public boolean isUiAwaiting() {
        return uiAwaiting;
    }

    public boolean isUiEnrolled() {
        return uiEnrolled;
    }

    public String getEditingId() {
        return editingId;
    }

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public SecurityKey getNewDevice() {
        return newDevice;
    }

    public List<FidoDevice> getDevices() {
        return devices;
    }

    public void setEditingId(String editingId) {
        this.editingId = editingId;
    }

    public void setNewDevice(SecurityKey newDevice) {
        this.newDevice = newDevice;
    }

    @Init(superclass = true)
    public void childInit() throws Exception {
        mapper = new ObjectMapper();
        newDevice = new SecurityKey();
        uiPanelOpened = true;
        devices = u2fService.getDevices(user.getId(), true);
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view) {
        Selectors.wireEventListeners(view, this);
    }

    @Command
    public void triggerU2fRegisterRequest() {
        try {
            uiAwaiting = true;
            BindUtils.postNotifyChange(null, null, this, "uiAwaiting");

            String JsonRequest = u2fService.generateJsonRegisterMessage(user.getUserName(), userService.generateRandEnrollmentCode(user.getId()));

            //Notify browser to exec proper function
            Clients.showNotification(Labels.getLabel("usr.u2f_touch"), Clients.NOTIFICATION_TYPE_INFO, null, "middle_center", WAIT_ENROLL_TIME);
            Clients.response(new AuInvoke("triggerU2fRegistration", new JavaScriptValue(JsonRequest), REGISTRATION_TIMEOUT));
        } catch (Exception e) {
            Utils.showMessageUI(false);
            logger.error(e.getMessage(), e);
        }

    }

    @Listen("onData=#readyButton")
    public void notified(Event event) throws Exception {

        String JsonStr = mapper.writeValueAsString(event.getData());
        String error = u2fService.getRegistrationResult(JsonStr);

        if (error == null) {
            u2fService.finishRegistration(user.getUserName(), JsonStr);
            //To know exactly which entry is, we pass the current timestamp so we can pick the most suitable
            //entry by inspecting the creationDate attribute among all existing entries
            newDevice = u2fService.getLatestSecurityKey(user.getId(), new Date().getTime());

            if (newDevice != null) {
                uiEnrolled = true;
                BindUtils.postNotifyChange(null, null, this, "uiEnrolled");
            } else {
                Utils.showMessageUI(false);
                logger.error(Labels.getLabel("app.finish_registration_error"));
            }
        } else {
            Utils.showMessageUI(false, Labels.getLabel("general.error.detailed", new String[] { error }));
        }

        uiAwaiting = false;
        BindUtils.postNotifyChange(null, null, this, "uiAwaiting");
        userService.cleanRandEnrollmentCode(user.getId());

    }

    @NotifyChange( {"uiPanelOpened", "uiEnrolled", "newDevice", "devices"} )
    @Command
    public void add() {

        if (Utils.isNotEmpty(newDevice.getNickName())) {
            try {
                u2fService.updateDevice(newDevice);
                devices.add(newDevice);
                Utils.showMessageUI(true, Labels.getLabel("usr.enroll.success"));
            } catch (Exception e) {
                Utils.showMessageUI(false, Labels.getLabel("usr.error_updating"));
                logger.error(e.getMessage(), e);
            }
            uiPanelOpened = false;
            resetAddSettings();
        }

    }

    private void resetAddSettings() {
        uiEnrolled = false;
        newDevice = new SecurityKey();
    }

    @NotifyChange({"uiEnrolled", "newDevice"})
    @Command
    public void cancel() {

        boolean success;
        try {
            /*
             Remove the recently enrolled key. This is so because once the user touches his key button, oxAuth creates the
             corresponding entry in LDAP, and if the user regrets adding the current key by not supplying a nickname
             (and thus pressing cancel), we need to be obliterate the entry
             */
            success = u2fService.removeDevice(newDevice);
        } catch (Exception e) {
            success = false;
            logger.error(e.getMessage(), e);
        }
        if (!success) {
            Utils.showMessageUI(false);
        }
        resetAddSettings();

    }

    @NotifyChange({"editingId", "newDevice"})
    @Command
    public void prepareForUpdate(@BindingParam("device") FidoDevice dev) {
        //This will make the modal window to become visible
        editingId = dev.getId();
        newDevice = new SecurityKey();
        newDevice.setNickName(dev.getNickName());
    }

    @NotifyChange({"editingId", "newDevice"})
    @Command
    public void cancelUpdate() {
        newDevice.setNickName(null);
        editingId = null;
    }

    @NotifyChange({"devices", "editingId", "newDevice"})
    @Command
    public void update() {

        String nick = newDevice.getNickName();
        if (Utils.isNotEmpty(nick)) {
            int i = Utils.firstTrue(devices, dev -> dev.getId().equals(editingId));
            FidoDevice dev = devices.get(i);
            dev.setNickName(nick);
            cancelUpdate();

            try {
                u2fService.updateDevice(dev);
                Utils.showMessageUI(true);
            } catch (Exception e) {
                Utils.showMessageUI(false);
                logger.error(e.getMessage(), e);
            }
        }

    }

    @Command
    public void delete(@BindingParam("device") FidoDevice device) {
        //TODO: impl
        //processFidoDeviceRemoval(device, keys.size(), this);
    }

}
