package org.gluu.credmanager.ui.vm;

import com.lochbridge.oath.otp.keyprovisioning.OTPKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.OTPConfig;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.services.OTPService;
import org.gluu.credmanager.services.ServiceMashup;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.json.JavaScriptValue;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.util.Clients;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by jgomer on 2017-08-01.
 */
public class UserOTPViewModel extends BasicViewModel {

    private static final int QR_SCAN_TIMEOUT=60;

    private ServiceMashup services;
    private OTPService otpService;

    private User user;
    private OTPConfig otpConfig;
    private byte secretKey[];
    private String code;

    private boolean uiPanelOpened;
    private boolean uiQRShown;

    private Logger logger = LogManager.getLogger(getClass());

    public boolean isUiPanelOpened() {
        return uiPanelOpened;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public boolean isUiQRShown() {
        return uiQRShown;
    }

    public void setUiPanelOpened(boolean uiPanelOpened) {
        this.uiPanelOpened = uiPanelOpened;
    }

    @Init(superclass = true)
    public void childInit(){

        Session se= Sessions.getCurrent();
        services= WebUtils.getServices(se);
        user=WebUtils.getUser(se);
        otpService=services.getOtpService();
        otpConfig=services.getAppConfig().getConfigSettings().getOtpConfig();
        uiPanelOpened=true;
    }

    private String getFormatedQROptions(OTPConfig config){

        List<String> list=new ArrayList<>();

        int ival=config.getQrSize();
        if (ival>0)
            list.add("size:" + ival);

        double dval=config.getQrMSize();
        if (dval>0)
            list.add("mSize: " + dval);

        return list.toString().replaceFirst("\\[","{").replaceFirst("\\]","}");

    }

    @Command
    public void showQR(){
        uiQRShown=true;
        BindUtils.postNotifyChange(null,	null, this, "uiQRShown");

        secretKey=otpService.generateSecretKey(otpConfig.getKeyLength());
        String request=otpService.generateSecretKeyUri(secretKey, otpConfig, user.getGivenName());
        JavaScriptValue jvalue=new JavaScriptValue(getFormatedQROptions(otpConfig));

        Clients.response(new AuInvoke("startQR", request, otpConfig.getLabel(), jvalue, QR_SCAN_TIMEOUT));
    }

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view){
        Selectors.wireEventListeners(view, this);
    }

    @Listen("onData=#readyButton")
    public void timedOut(Event event) throws Exception {
        if (uiQRShown) {
            uiQRShown = false;
            BindUtils.postNotifyChange(null, null, this, "uiQRShown");
        }
    }

    @NotifyChange({"uiQRShown","code"})
    @Command
    public void cancel(){
        Clients.response(new AuInvoke("clean"));
        uiQRShown=false;
        code=null;
    }

    @NotifyChange({"uiQRShown","code","uiPanelOpened"})
    @Command
    public void enroll(){

        String uid=null;
        if (otpConfig.getType().equals(OTPKey.OTPType.HOTP)) {
            Pair<Boolean, Long> result=otpService.validateHOTPKey(secretKey, 1, code, otpConfig.getDigits());
            if (result.getX())
                uid=otpService.getExternalHOTPUid(secretKey, result.getY());
        }
        else
        if (otpService.validateTOTPKey(otpConfig, secretKey, code))
            uid=otpService.getExternalTOTPUid(secretKey);

        if (uid==null) {
            //TODO: handle enrollment fail
            logger.debug("error enroll");
        }
        else{
            logger.debug(uid);
            uiPanelOpened=false;
            cancel();
        }
    }

}