package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.ui.model.UIModel;
import org.zkoss.bind.annotation.*;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zul.ListModelSet;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Created by jgomer on 2017-07-22.
 */
public class UserPreferenceViewModel extends UserViewModel {

    private Logger logger = LogManager.getLogger(getClass());

    private String noMethodName;
    private CredentialType prevSelectedMethod;
    private CredentialType selectedMethod;
    private ListModelSet<Pair<CredentialType, String>> availMethods;

    private boolean uiEditing;
    private boolean uiEditable;
    private boolean uiNotEnoughCredsFor2FA;

    public int getMinimumCredsFor2FA(){
        return AppConfiguration.ACTIVATE2AF_CREDS_GTE;
    }

    public boolean isUiNotEnoughCredsFor2FA(){
        return uiNotEnoughCredsFor2FA;
    }

    @Init(superclass = true)
    public void childInit() throws Exception {

        selectedMethod = user.getPreference();

        noMethodName=Labels.getLabel("usr.method.none");
        Set<CredentialType> enabledMethods=services.getAppConfig().getEnabledMethods();
        availMethods = UIModel.getCredentialList(services.getUserService().getEffectiveMethods(user, enabledMethods));

        int totalCreds = user.getCredentials().values().stream().mapToInt(List::size).sum();
        logger.info(Labels.getLabel("app.credentials_total"), user.getUserName(), totalCreds);

        //Note: It may happen user already has enrolled credentials, but admin changed availability of method. In that
        //case user should not be able to edit
        uiEditable = totalCreds >= getMinimumCredsFor2FA() && availMethods.size()>0;
        uiNotEnoughCredsFor2FA = totalCreds < getMinimumCredsFor2FA() && enabledMethods.size()>0;

        availMethods.add(new Pair<>(null, noMethodName));

    }

    public CredentialType getSelectedMethod() {
        return selectedMethod;
    }

    @DependsOn("selectedMethod")
    public String getSelectedMethodName() {
        Optional<String> optCred = Optional.ofNullable(selectedMethod).map(CredentialType::getUIName);
        return optCred.orElse(noMethodName);
    }

    public boolean isUiEditing() {
        return uiEditing;
    }

    public boolean isUiEditable() {
        return uiEditable;
    }

    public ListModelSet<Pair<CredentialType, String>> getAvailMethods() {
        return availMethods;
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
        if (services.getUserService().setPreferredMethod(user, selectedMethod))
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