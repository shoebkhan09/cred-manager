/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.plugins.authnmethod;

import org.gluu.credmanager.credential.BasicCredential;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.plugins.authnmethod.service.SGService;
import org.pf4j.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jgomer
 */
@Extension
public class SuperGluuExtension implements AuthnMethod {

    public static final String ACR = "super_gluu";

    private Logger logger = LoggerFactory.getLogger(getClass());

    private SGService sgService;

    public SuperGluuExtension() {
        sgService = Utils.managedBean(SGService.class);
    }

    public String getUINameKey() {
        return "general.credentials.SUPER_GLUU";
    }

    public String getName() {
        return "Super gluu";
    }

    public String getAcr() {
        return ACR;
    }

    public String getPanelTitleKey() {
        return "usr.supergluu_title";
    }

    public String getPanelTextKey() {
        return "usr.supergluu_text";
    }

    public String getPanelButtonKey() {
        return "usr.supergluu_changeadd";
    }

    public String getPanelBottomTextKey() {
        return "usr.supergluu_download";
    }

    public String getPageUrl() {
        return "user/super-detail.zul";
    }

    public List<BasicCredential> getEnrolledCreds(String id) {
        return getEnrolledCreds(id, true);
    }

    public List<BasicCredential> getEnrolledCreds(String id, boolean active) {
        try {
            return sgService.getDevices(id, active).stream()
                    .map(dev -> new BasicCredential(dev.getNickName(), dev.getCreationDate().getTime())).collect(Collectors.toList());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public int getTotalUserCreds(String id) {
        return getTotalUserCreds(id, true);
    }

    public int getTotalUserCreds(String id, boolean valid) {
        return sgService.getDevicesTotal(id, valid);
    }

}
