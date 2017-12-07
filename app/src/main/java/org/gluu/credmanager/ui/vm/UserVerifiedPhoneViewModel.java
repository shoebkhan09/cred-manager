package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.core.credential.VerifiedPhone;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.SmsService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zul.Messagebox;

import java.util.*;

/**
 * Created by jgomer on 2017-07-25.
 * This is the ViewModel of page phone-detail.zul. It controls the CRUD of verified phones
 */
public class UserVerifiedPhoneViewModel extends UserViewModel{

    private Logger logger = LogManager.getLogger(getClass());

    private boolean uiCodesMatch;
    private boolean uiPanelOpened;

    private List<RegisteredCredential> phones;
    private VerifiedPhone newPhone;
    private String code;
    private String realCode;
    private String editingNumber;
    private SmsService smsService;

    public String getEditingNumber() {
        return editingNumber;
    }

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public VerifiedPhone getNewPhone() {
        return newPhone;
    }

    public List<RegisteredCredential> getPhones() {
        return phones;
    }

    public String getCode() {
        return code;
    }

    public boolean isUiCodesMatch() {
        return uiCodesMatch;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public void setNewPhone(VerifiedPhone newPhone) {
        this.newPhone = newPhone;
    }

    public void setUiPanelOpened(boolean uiPanelOpened) {
        this.uiPanelOpened = uiPanelOpened;
    }

    @Init(superclass = true)
    public void childInit() throws Exception{
        newPhone=new VerifiedPhone(null);

        devices=user.getCredentials().get(CredentialType.VERIFIED_PHONE);
        phones=devices;
        smsService=services.getSmsService();
        uiPanelOpened=true;
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    @Command
    public void sendCode(){

        if (Utils.stringOptional(newPhone.getNumber()).isPresent()) {   //Did user fill out the phone text box?
            //Check for uniquess throughout all phones in LDAP. Only new numbers are accepted
            try {
                if (!smsService.isPhoneNumberUnique(newPhone))
                    Messagebox.show(Labels.getLabel("usr.mobile_already_exists"), Labels.getLabel("general.warning"), Messagebox.OK, Messagebox.INFORMATION);
                else {
                    //Generate random in [100000, 999999]
                    realCode = Integer.toString(new Double(100000 + Math.random() * 899999).intValue());
                    //Compose SMS body
                    String body = services.getAppConfig().getOrgName();
                    body = Labels.getLabel("usr.mobile_sms_body", new String[]{body, realCode});
                    logger.trace("sendCode. code={}", realCode);

                    //Send message (service bean already knows all settings to perform this step)
                    smsService.sendSMS(newPhone.getNumber(), body);
                    Messagebox.show(Labels.getLabel("usr.mobile_sms_sent", new String[]{newPhone.getNumber()}), null, Messagebox.OK, Messagebox.INFORMATION);
                }
            }
            catch (Exception e) {
                showMessageUI(false);
                logger.error(e.getMessage(), e);
            }
        }
    }

    @NotifyChange("uiCodesMatch")
    @Command
    public void checkCode(){
        uiCodesMatch=Utils.stringOptional(code).isPresent() && Utils.stringOptional(realCode).isPresent() && realCode.equals(code.trim());
    }

    @NotifyChange({"uiCodesMatch", "code", "phones", "uiPanelOpened", "newPhone"})
    @Command
    public void add(){

        if (Utils.stringOptional(newPhone.getNickName()).isPresent()) {
            try {
                newPhone.setAddedOn(new Date().getTime());
                userService.updateMobilePhonesAdd(user, phones, newPhone);
                showMessageUI(true, Labels.getLabel("usr.enroll.success"));
            }
            catch (Exception e) {
                showMessageUI(false, Labels.getLabel("usr.enroll.error"));
                logger.error(e.getMessage(), e);
            }
            cancel();
        }

    }

    @NotifyChange({"uiCodesMatch", "code", "uiPanelOpened", "newPhone"})
    @Command
    public void cancel(){
        resetAddPhoneSettings();
        uiPanelOpened = false;
    }

    private void resetAddPhoneSettings(){
        uiCodesMatch=false;
        realCode=null;
        code=null;
        newPhone=new VerifiedPhone();
    }

    @NotifyChange({"newPhone", "editingNumber"})
    @Command
    public void cancelUpdate(){
        newPhone.setNickName(null);
        editingNumber=null;
    }

    @NotifyChange({"newPhone", "editingNumber"})
    @Command
    public void prepareForUpdate(@BindingParam("phone") VerifiedPhone phone){
        //This will make the modal window to become visible
        editingNumber=phone.getNumber();
        newPhone=new VerifiedPhone("");
        newPhone.setNickName(phone.getNickName());
    }

    @NotifyChange({"newPhone", "phones", "editingNumber"})
    @Command
    public void update(){

        String nick=newPhone.getNickName();
        if (Utils.stringOptional(nick).isPresent()) {
            int i=Utils.firstTrue(phones, VerifiedPhone.class::cast, p -> p.getNumber().equals(editingNumber));
            VerifiedPhone ph=(VerifiedPhone) phones.get(i);
            ph.setNickName(nick);
            cancelUpdate();

            try {
                userService.updateMobilePhonesAdd(user, phones, null);
                showMessageUI(true);
            }
            catch (Exception e){
                showMessageUI(false);
                logger.error(e.getMessage(), e);
            }
        }

    }

    @Command
    public void delete(@BindingParam("phone") VerifiedPhone phone){

        boolean flag=mayTriggerResetPreference(CredentialType.VERIFIED_PHONE, devices.size());
        Pair<String, String> delMessages=getDelMessages(flag, phone.getNickName());

        Messagebox.show(delMessages.getY(), delMessages.getX(), Messagebox.YES | Messagebox.NO, flag ? Messagebox.EXCLAMATION : Messagebox.QUESTION,
                event ->  {
                    if (Messagebox.ON_YES.equals(event.getName())) {
                        try {
                            if (phones.remove(phone)) {
                                if (flag)
                                    userService.setPreferredMethod(user,null);

                                userService.updateMobilePhonesAdd(user, phones, null);
                                //trigger refresh (this method is asynchronous...)
                                BindUtils.postNotifyChange(null, null, UserVerifiedPhoneViewModel.this, "phones");
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