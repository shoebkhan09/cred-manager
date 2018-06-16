/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm.user;

import org.gluu.credmanager.core.pojo.OTPDevice;
import org.gluu.credmanager.plugins.authnmethod.service.OTPService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

/**
 * This is the ViewModel of page otp-detail.zul. It controls the CRUD of HOTP/TOTP devices
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class OTPViewModel extends UserViewModel {

    private static final int QR_SCAN_TIMEOUT = 60;

    private Logger logger = LoggerFactory.getLogger(getClass());

    private OTPService otpService;

    private String code;
    private OTPDevice newDevice;
    private int editingId;

    private boolean uiPanelOpened;
    private boolean uiQRShown;
    private boolean uiCorrectCode;

    public boolean isUiCorrectCode() {
        return uiCorrectCode;
    }

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public boolean isUiQRShown() {
        return uiQRShown;
    }

    public int getEditingId() {
        return editingId;
    }

    public String getCode() {
        return code;
    }

    public OTPDevice getNewDevice() {
        return newDevice;
    }

    public void setNewDevice(OTPDevice newDevice) {
        this.newDevice = newDevice;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setUiPanelOpened(boolean uiPanelOpened) {
        this.uiPanelOpened = uiPanelOpened;
    }

}
