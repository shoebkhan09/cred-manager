package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.UserService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;

import java.util.*;

/**
 * Created by jgomer on 2017-07-08.
 * This is the ViewModel of page user.zul (the main page of this app).
 * Main functionalities controlled here are: password reset if available and summary of users's enrolled devices by type
 */
public class UserMainViewModel extends UserViewModel{

    private Logger logger = LogManager.getLogger(getClass());

    private String introText;
    private Map<String, Boolean> credentialsVisibility;
    private boolean secondFactorAllowed;

    private String currentPassword;
    private String newPassword;
    private String newPasswordConfirm;
    private int strength;

    private boolean uiPanelOpened;
    private boolean uiPassResetAvailable;

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public boolean isUiPassResetAvailable() {
        return uiPassResetAvailable;
    }

    @DependsOn("strength")
    public String getStrengthText() {
        String str=null;
        if (strength>=0) {
            str=Labels.getLabel("usr.pass.strength.level." + strength);
            //str=Labels.getLabel("usr.pass.strength.title", new String[]{str});
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

    public String getIntroText(){
        return introText;
    }

    public Map<String, Boolean> getCredentialsVisibility() {
        return credentialsVisibility;
    }

    public boolean isSecondFactorAllowed() {
        return secondFactorAllowed;
    }

    @Init(superclass = true)
    public void childInit() throws Exception{

        uiPanelOpened=true;
        strength=-1;
        AppConfiguration appConfig=services.getAppConfig();
        uiPassResetAvailable=appConfig.isPassReseteable();

        if (user.getCredentials()!=null) {
            StringBuffer helper=new StringBuffer();
            credentialsVisibility=new HashMap<>();
            Set<CredentialType> systemLevelCreds=appConfig.getEnabledMethods();

            for (CredentialType cred : CredentialType.values()){
                boolean flag=systemLevelCreds.contains(cred);

                credentialsVisibility.put(cred.name(), flag);
                if (flag)
                    helper.append(", ").append(Labels.getLabel("usr.main_intro." + cred));
            }

            secondFactorAllowed=systemLevelCreds.size()>0;
            if (secondFactorAllowed)
                introText = Labels.getLabel("usr.main_intro", new String[]{appConfig.getOrgName(), helper.substring(2)});
        }
        else
            logger.error(Labels.getLabel("app.fail_read_credentials"));

    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    @Listen("onData=#new_pass")
    public void notified(Event event) throws Exception{
        if (Utils.stringOptional(newPassword).isPresent())
            strength=(int)event.getData();
        else
            strength=-1;
        BindUtils.postNotifyChange(null,	null, this, "strength");
    }

    @NotifyChange({"newPassword", "newPasswordConfirm", "currentPassword", "strength", "uiPanelOpened"})
    @Command
    public void resetPass(){
        UserService usrService=services.getUserService();

        boolean match=false;
        try {
            match=usrService.currentPasswordMatch(user, currentPassword);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        if (match){
            if (newPasswordConfirm!=null && newPasswordConfirm.equals(newPassword))
                if (usrService.changePassword(user, newPassword)) {
                    logger.info(Labels.getLabel("app.pass_resetted"), user.getUserName());
                    resetPassSettings();
                    uiPanelOpened = false;
                    showMessageUI(true, Labels.getLabel("usr.passreset_changed"), "bottom_center");
                }
                else
                    showMessageUI(false);
            else {
                showMessageUI(false, Labels.getLabel("usr.passreset_nomatch"), "bottom_center");
                newPasswordConfirm = null;
                newPassword=null;
                strength=-1;
            }
        }
        else{
            currentPassword=null;
            showMessageUI(false, Labels.getLabel("usr.passreset_badoldpass"), "bottom_center");
        }

    }

    public void resetPassSettings(){
        newPassword=null;
        newPasswordConfirm=null;
        currentPassword=null;
        strength=-1;
    }

    @NotifyChange({"newPassword", "newPasswordConfirm", "currentPassword", "strength"})
    @Command
    public void cancel(){
        resetPassSettings();
    }

}