package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
import org.gluu.credmanager.services.OxdService;
import org.gluu.credmanager.ui.model.UIModel;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.*;
import org.zkoss.zul.ListModelSet;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gluu.credmanager.core.WebUtils.RedirectStage;
import static org.gluu.credmanager.core.WebUtils.RedirectStage.REAUTHENTICATE;

/**
 * Created by jgomer on 2017-07-19.
 * This class handles a reauthentication scenario. Methods in this class are executed only if current user has a preferred
 * method of authn, and is presented a form to validate using such method and then redirected to IDP for credentials
 */
public class HomeViewModel{

    private Logger logger = LogManager.getLogger(getClass());

    private ServiceMashup services;
    private User user;
    private Session se;
    private RedirectStage stage;
    private boolean immediateRedir;

    private CredentialType selectedMethod;
    private ListModelSet<Pair<CredentialType, String>> availMethods;

    private String hint;

    public boolean isImmediateRedir() {
        return immediateRedir;
    }

    public String getHint() {
        return hint;
    }

    public CredentialType getSelectedMethod() {
        return selectedMethod;
    }

    public ListModelSet<Pair<CredentialType, String>> getAvailMethods() {
        return availMethods;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    @Init
    public void init(){

        try {
            se=Sessions.getCurrent();
            stage=WebUtils.getRedirectStage(se);
            user=WebUtils.getUser(se);

            if (stage.equals(REAUTHENTICATE)){

                services=WebUtils.getServices(se);

                Set<CredentialType> enabledMethods=services.getUserService().getEffectiveMethods(user, services.getAppConfig().getEnabledMethods());
                immediateRedir=enabledMethods.size()<2;     //This means enabled method == preferred cred type

                if (immediateRedir) {  //No need to select anything, redirecting now
                    setHint(Labels.getLabel("usr.reauthn.progress"));
                    reauthRedirect(user.getPreference());
                }
                else{
                    //show in this page the method selector
                    setHint(Labels.getLabel("usr.method.choose"));

                    //get a model for list of methods
                    availMethods = UIModel.getCredentialList(enabledMethods);
                    selectedMethod = user.getPreference();
                }
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

    private void reauthRedirect(CredentialType preferred){

        try {
            OxdService oxdService=services.getOxdService();

            WebUtils.setRedirectStage(se, RedirectStage.FINAL);
            //do second Authz Redirect

            /*
            TODO: strange! if one sends a compounded acr (e.g a list with size>1), oxAuth's
            SessionStateService#assertAuthenticatedSessionCorrespondsToNewRequest complains. Does it make sense?
            ... resorting to passing a sublist by now
             */
            String url=oxdService.getAuthzUrl(preferred.getAcrsList().subList(0,1), "login");
            logger.debug("reautenticating with url {}", url);
            Executions.sendRedirect(url);
        }
        catch (Exception e){

            logger.error(e.getMessage(), e);
        }

    }

    @Command
    public void reauthenticate(){
        reauthRedirect(selectedMethod);
    }

    @Command
    public void change(@BindingParam("method") CredentialType cred){
        selectedMethod=cred;
    }

}