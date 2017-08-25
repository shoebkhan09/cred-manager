package org.gluu.credmanager.ui.vm;

import com.twilio.sdk.resource.instance.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.core.credential.VerifiedPhone;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.UserService;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zul.Messagebox;

import static org.gluu.credmanager.conf.CredentialType.VERIFIED_PHONE;

import java.util.*;

/**
 * Created by jgomer on 2017-07-25.
 */
public class UserVerifiedPhoneViewModel extends UserViewModel{

    private Logger logger = LogManager.getLogger(getClass());

    private List<RegisteredCredential> phones;
    private VerifiedPhone newPhone;
    private String code;
    private String realCode;
    private String editingNumber;

    private boolean uiAwaitingArrival;
    private boolean uiCodesMatch;
    private boolean uiPanelOpened;

    public String getEditingNumber() {
        return editingNumber;
    }

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public boolean isUiAwaitingArrival() {
        return uiAwaitingArrival;
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

        uiPanelOpened=true;
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    @Listen("onData=#sendButton")
    public void notified(Event event) throws Exception {
        if (uiAwaitingArrival) {
            uiAwaitingArrival = false;
            BindUtils.postNotifyChange(null, null, this, "uiAwaitingArrival");
        }
    }

    @NotifyChange("uiAwaitingArrival")
    @Command
    public void sendCode(){

        if (Utils.stringOptional(newPhone.getNumber()).isPresent()) {
            int i=Utils.firstTrue(phones, VerifiedPhone.class::cast, p -> p.getNumber().equals(newPhone.getNumber()));
            if (i>=0)
                Messagebox.show(Labels.getLabel("usr.mobile_already_exists"), Labels.getLabel("general.warning"), Messagebox.OK, Messagebox.INFORMATION);
            else
                try {
                    uiAwaitingArrival = true;
                    BindUtils.postNotifyChange(null, null, this, "uiAwaitingArrival");

                    //Generate random in [100000, 999999]
                    realCode = Integer.toString(new Double(100000 + Math.random() * 899999).intValue());
                    String body = services.getAppConfig().getOrgName();
                    body = Labels.getLabel("usr.mobile_sms_body", new String[]{body, realCode});
logger.debug("CODE={}", realCode);

                    services.getSmsService().sendSMS(newPhone.getNumber(), body);

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

        try {
            newPhone.setAddedOn(new Date().getTime());
            services.getUserService().updateMobilePhonesAdd(user, phones, newPhone);
            cancel();
            showMessageUI(true, Labels.getLabel("usr.enroll.success"));
        }
        catch (Exception e){
            showMessageUI(false, Labels.getLabel("usr.enroll.error"));
            logger.error(e.getMessage(), e);
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
        if (nick!=null){
            int i=Utils.firstTrue(phones, VerifiedPhone.class::cast, p -> p.getNumber().equals(editingNumber));
            VerifiedPhone ph=(VerifiedPhone) phones.get(i);
            ph.setNickName(nick);
            cancelUpdate();

            try {
                services.getUserService().updateMobilePhonesAdd(user, phones, null);
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

        boolean flag=mayTriggerResetPreference(user.getPreference(), devices, VERIFIED_PHONE);
        Pair<String, String> delMessages=getDelMessages(flag, VERIFIED_PHONE, phone.getNickName());

        Messagebox.show(delMessages.getY(), delMessages.getX(), Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                event ->  {
                    if (Messagebox.ON_YES.equals(event.getName())) {
                        UserService usrService = services.getUserService();
                        try {
                            if (phones.remove(phone)) {
                                if (flag)
                                    usrService.setPreferredMethod(user,null);

                                usrService.updateMobilePhonesAdd(user, phones, null);
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