/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod;

import org.gluu.credmanager.credential.BasicCredential;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.plugins.BaseSystemExtension;
import org.gluu.credmanager.plugins.authnmethod.service.OTPService;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Note: No injection can take place at extensions because instances are handled by p4fj
 * @author jgomer
 */
@Extension
public class OTPExtension extends BaseSystemExtension implements AuthnMethod {

    public static final String ACR = "otp";

    private Logger logger = LoggerFactory.getLogger(getClass());

    private OTPService otpService;

    public OTPExtension() {
        otpService = Utils.managedBean(OTPService.class);
    }

    public String getUINameKey() {
        return "general.credentials.OTP";
    }

    public String getName() {
        return "OTPli";
    }

    public String getAcr() {
        return ACR;
    }

    public String getPanelTitleKey() {
        return "usr.gauth_title";
    }

    public String getPanelTextKey() {
        return "usr.gauth_text";
    }

    public String getPanelButtonKey() {
        return "usr.gauth_changeadd";
    }

    public String getPanelBottomTextKey() {
        return "usr.gauth_download";
    }

    public String getPageUrl() {
        return "user/otp-detail.zul";
    }

    public List<BasicCredential> getEnrolledCreds(String id) {
        return getEnrolledCreds(id, true);
    }

    public List<BasicCredential> getEnrolledCreds(String id, boolean valid) {

        try {
            return otpService.getOTPDevices(id).stream()
                    .map(dev -> new BasicCredential(dev.getNickName(), dev.getAddedOn())).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public int getTotalUserCreds(String id) {
        return getTotalUserCreds(id, true);
    }

    public int getTotalUserCreds(String id, boolean valid) {
        return otpService.getDevicesTotal(id);
    }

    public boolean mayBe2faActivationRequisite() {
        return true;
    }

    public void reloadConfiguration() {
        otpService.reloadConfiguration();
    }

}
