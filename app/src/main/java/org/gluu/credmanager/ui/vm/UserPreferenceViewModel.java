/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.conf.sndfactor.EnforcementPolicy;
import org.gluu.credmanager.ui.model.UIModel;
import org.zkoss.bind.annotation.*;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zul.ListModelList;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Created by jgomer on 2017-07-22.
 * This is the ViewModel of page fragment preferred.zul (and the fragments included by it). It controls the functionality
 * for setting the user's preferred authentication method when second factor authentication is enabled
 */
public class UserPreferenceViewModel extends UserViewModel {

    private Logger logger = LogManager.getLogger(getClass());

    private String noMethodName;
    private CredentialType prevSelectedMethod;
    private CredentialType selectedMethod;
    private ListModelList<Pair<CredentialType, String>> availMethods;

    private boolean uiEditing;
    private boolean uiEditable;
    private boolean uiNotEnoughCredsFor2FA;
    private boolean uiAllowedToSetPolicy;

    public boolean isUiEditing() {
        return uiEditing;
    }

    public boolean isUiEditable() {
        return uiEditable;
    }

    public ListModelList<Pair<CredentialType, String>> getAvailMethods() {
        return availMethods;
    }

    public boolean isUiNotEnoughCredsFor2FA(){
        return uiNotEnoughCredsFor2FA;
    }

    public boolean isUiAllowedToSetPolicy() {
        return uiAllowedToSetPolicy;
    }

    @Init(superclass = true)
    public void childInit() throws Exception {

        selectedMethod = user.getPreference();

        noMethodName=Labels.getLabel("usr.method.none");
        Set<CredentialType> enabledMethods=services.getAppConfig().getEnabledMethods();
        availMethods = UIModel.getCredentialList(userService.getEffectiveMethods(user));

        int totalCreds = user.getCredentials().values().stream().mapToInt(List::size).sum();
        logger.info(Labels.getLabel("app.credentials_total"), user.getUserName(), totalCreds);

        //Note: It may happen user already has enrolled credentials, but admin changed availability of method. In that
        //case user should not be able to edit
        uiEditable = totalCreds >= getMinimumCredsFor2FA() && availMethods.size()>0;
        uiNotEnoughCredsFor2FA = totalCreds < getMinimumCredsFor2FA() && enabledMethods.size()>0;

        availMethods.add(new Pair<>(null, noMethodName));
        uiAllowedToSetPolicy = services.getAppConfig().getConfigSettings().getEnforcement2FA().contains(EnforcementPolicy.CUSTOM);

    }

    public CredentialType getSelectedMethod() {
        return selectedMethod;
    }

    @DependsOn("selectedMethod")
    public String getSelectedMethodName() {
        Optional<String> optCred = Optional.ofNullable(selectedMethod).map(CredentialType::getUIName);
        return optCred.orElse(noMethodName);
    }

    @NotifyChange({"uiEditing","selectedMethod"})
    @Command
    public void cancel(){
        uiEditing=false;
        selectedMethod=prevSelectedMethod;
    }

    @NotifyChange({"uiEditing","selectedMethod"})
    @Command
    public void update(){

        uiEditing = false;
        //saves to LDAP and updates user object afterwards
        if (userService.setPreferredMethod(user, selectedMethod))
            showMessageUI(true);
        else{
            selectedMethod=prevSelectedMethod;
            showMessageUI(false);
        }

    }

    @NotifyChange({"uiEditing"})
    @Command
    public void prepareUpdateMethod(){
        prevSelectedMethod=selectedMethod;
        uiEditing =true;
    }

    @Command
    public void change(@BindingParam("method") CredentialType cred){
        selectedMethod=cred;
    }

}