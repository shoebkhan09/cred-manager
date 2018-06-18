/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm.user;

import org.gluu.credmanager.core.ConfigurationHandler;
import org.gluu.credmanager.core.ExtensionsManager;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.service.LdapService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.AfterCompose;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.ContextParam;
import org.zkoss.bind.annotation.ContextType;
import org.zkoss.bind.annotation.DependsOn;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This is the ViewModel of page user.zul (the main page of this app).
 * Main functionalities controlled here are: password reset if available and summary of users's enrolled devices by type
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class UserMainViewModel extends UserViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable("configurationHandler")
    private ConfigurationHandler confHandler;

    @WireVariable
    private LdapService ldapService;

    @WireVariable("extensionsManager")
    private ExtensionsManager extManager;

    private String introText;
    private boolean methodsAvailability;
    private boolean has2faRequisites;

    private List<AuthnMethod> widgets;
    private List<AuthnMethod> pre2faMethods;

    private String currentPassword;
    private String newPassword;
    private String newPasswordConfirm;
    private int strength;

    private boolean uiPwdResetOpened;

    public String getIntroText() {
        return introText;
    }

    public boolean isUiPwdResetOpened() {
        return uiPwdResetOpened;
    }

    public boolean isMethodsAvailability() {
        return methodsAvailability;
    }

    public boolean isHas2faRequisites() {
        return has2faRequisites;
    }

    @DependsOn("strength")
    public String getStrengthText() {
        String str = null;
        if (strength >= 0) {
            str = Labels.getLabel("usr.pass.strength.level." + strength);
        }
        return str;
    }

    public int getStrength() {
        return strength;
    }

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }

    public String getNewPasswordConfirm() {
        return newPasswordConfirm;
    }

    public void setNewPasswordConfirm(String newPasswordConfirm) {
        this.newPasswordConfirm = newPasswordConfirm;
    }

    public List<AuthnMethod> getWidgets() {
        return widgets;
    }

    public List<AuthnMethod> getPre2faMethods() {
        return pre2faMethods;
    }

    @Init(superclass = true)
    public void childInit() {
        uiPwdResetOpened = true;
        strength = -1;

        widgets = userService.getLiveAuthnMethods();
        methodsAvailability = widgets.size() > 0;

        if (methodsAvailability) {
            StringBuffer helper = new StringBuffer();
            widgets.forEach(aMethod -> helper.append(", ").append(aMethod.getPanelTitleKey()));
            String orgName = ldapService.getOrganization().getDisplayName();
            introText = Labels.getLabel("usr.main_intro", new String[] { orgName, helper.substring(2) });

            pre2faMethods = widgets.stream().filter(AuthnMethod::mayBe2faActivationRequisite).collect(Collectors.toList());
            has2faRequisites = pre2faMethods.stream().anyMatch(aMethod ->  aMethod.getTotalUserCreds(user.getId(), true) > 0);
        }

    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view) {
        Selectors.wireEventListeners(view, this);
    }

    @Listen("onData=#new_pass")
    public void notified(Event event) throws Exception {
        if (Utils.isNotEmpty(newPassword)) {
            strength = (int) event.getData();
        } else {
            strength = -1;
        }
        BindUtils.postNotifyChange(null, null, this, "strength");
    }

    @NotifyChange({"newPassword", "newPasswordConfirm", "currentPassword", "strength", "uiPanelOpened"})
    @Command
    public void resetPass() {

        if (userService.passwordMatch(user.getUserName(), currentPassword)) {
            if (newPasswordConfirm != null && newPasswordConfirm.equals(newPassword)) {

                if (userService.changePassword(user.getId(), newPassword)) {
                    logger.info(Labels.getLabel("app.pass_resetted"), user.getUserName());
                    resetPassSettings();
                    uiPwdResetOpened = false;
                    Utils.showMessageUI(true, Labels.getLabel("usr.passreset_changed"), "bottom_center");
                } else {
                    Utils.showMessageUI(false);
                }

            } else {
                Utils.showMessageUI(false, Labels.getLabel("usr.passreset_nomatch"), "bottom_center");
                newPasswordConfirm = null;
                newPassword = null;
                strength = -1;
            }
        } else {
            currentPassword = null;
            Utils.showMessageUI(false, Labels.getLabel("usr.passreset_badoldpass"), "bottom_center");
        }

    }

    public void resetPassSettings() {
        newPassword = null;
        newPasswordConfirm = null;
        currentPassword = null;
        strength = -1;
    }

    @NotifyChange({"newPassword", "newPasswordConfirm", "currentPassword", "strength"})
    @Command
    public void cancel() {
        resetPassSettings();
    }

}
