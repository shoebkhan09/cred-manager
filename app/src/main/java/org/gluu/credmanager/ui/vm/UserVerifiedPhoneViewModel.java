package org.gluu.credmanager.ui.vm;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.resource.factory.MessageFactory;
import com.twilio.sdk.resource.instance.Message;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.conf.jsonized.TwilioConfig;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.core.credential.VerifiedPhone;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ServiceMashup;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.zkoss.zk.ui.event.EventQueues;
import org.zkoss.zk.ui.util.Clients;

import java.util.ArrayList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-07-25.
 */
public class UserVerifiedPhoneViewModel extends BasicViewModel {

    private static final long ARRIVAL_DELAY=5000;    //5 secs

    private ServiceMashup services;
    private User user;

    private List<VerifiedPhone> phones;
    private VerifiedPhone newPhone;
    private String code;
    private String realCode;
    private String fromNumber;

    private boolean uiAwaitingArrival;
    private boolean uiCodesMatch;
    private boolean uiPanelOpened=true;

    private MessageFactory messageFactory;

    private Logger logger = LogManager.getLogger(getClass());

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public VerifiedPhone getNewPhone() {
        return newPhone;
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

    public boolean isUiAwaitingArrival() {
        return uiAwaitingArrival;
    }

    @Init(superclass = true)
    public void childInit() throws Exception{

        Session se= Sessions.getCurrent();
        services= WebUtils.getServices(se);
        user=WebUtils.getUser(se);
        TwilioConfig tCfg=services.getAppConfig().getConfigSettings().getTwilioConfig();

        TwilioRestClient client=new TwilioRestClient(tCfg.getAccountSID(), tCfg.getAuthToken());
        messageFactory = client.getAccount().getMessageFactory();

        fromNumber=tCfg.getFromNumber();
        newPhone=new VerifiedPhone(null);

        Stream<RegisteredCredential> stream=user.getCredentials().stream().filter(cred -> cred.getType().equals(CredentialType.VERIFIED_PHONE));
        phones=stream.map(VerifiedPhone.class::cast).collect(Collectors.toList());
    }

    //@NotifyChange("uiAwaitingArrival")
    @Command
    public void sendCode(){

        if (Utils.stringOptional(newPhone.getNumber()).isPresent()) {
            /*
            //A timer to make the button deactivated for a while
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    uiAwaitingArrival = false;

                    //BindUtils.postNotifyChange(null, EventQueues.DESKTOP, UserVerifiedPhoneModel.this, "uiAwaitingArrival");
                    logger.debug("timer");
                }
            }, ARRIVAL_DELAY);
            */
            try {
                //uiAwaitingArrival = true;
                //BindUtils.postNotifyChange(null, null, this, "uiAwaitingArrival");

                //Generate random in [100000, 999999]
                realCode = Integer.toString(new Double(100000 + Math.random() * 899999).intValue());
                String body = services.getAppConfig().getOrgName();
                body = Labels.getLabel("usr.mobile_sms_body", new Object[]{body, realCode});
//Remove this
logger.debug(realCode);
                List<NameValuePair> messageParams = new ArrayList<>();
                messageParams.add(new BasicNameValuePair("To", newPhone.getNumber()));
                messageParams.add(new BasicNameValuePair("From", fromNumber));
                messageParams.add(new BasicNameValuePair("Body", body));
                Message message = messageFactory.create(messageParams);

//Remove this
                logger.info(Labels.getLabel("app.sms_sent_summary"), message.getSid(), realCode, newPhone.getNumber(), message.getStatus());
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
//TODO: notify UI of error...
            }
        }
    }

    @NotifyChange("uiCodesMatch")
    @Command
    public void checkCode(){
        uiCodesMatch=Utils.stringOptional(code).isPresent() && Utils.stringOptional(realCode).isPresent() && realCode.equals(code.trim());
    }

    @NotifyChange({"uiCodesMatch", "code"})
    @Command
    public void addPhone(){

        try {
            services.getUserService().updateMobilePhones(user, phones, newPhone);
            resetAddPhoneSettings();
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
//TODO: notify UI of error...
        }
    }

    @NotifyChange({"uiCodesMatch", "code", "uiPanelOpened"})
    @Command
    public void cancelPhone(){

        resetAddPhoneSettings();
        uiPanelOpened = false;
    }

    private void resetAddPhoneSettings(){

        uiCodesMatch=false;
        realCode=null;
        code=null;
        newPhone.setNumber(null);
        newPhone.setNickName(null);
    }

}