package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
import org.gluu.credmanager.services.oxd.OxdService;
import org.gluu.credmanager.ui.model.UIModel;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.*;
import org.zkoss.zul.ListModelSet;

import java.util.*;

import org.gluu.credmanager.core.WebUtils.RedirectStage;
import static org.gluu.credmanager.core.WebUtils.RedirectStage.REAUTHENTICATE;

/**
 * Created by jgomer on 2017-07-19.
 */
public class HomeViewModel{

    private Logger logger = LogManager.getLogger(getClass());
    private ServiceMashup services;
    private Session se;
    private RedirectStage stage;
    private boolean immediateRedir;

    private ListModelSet<Pair<CredentialType, String>> availMethods;
    private int selectedMethodIndex;

    private String hint;

    public String getHint() {
        return hint;
    }

    public int getSelectedMethodIndex() {
        return selectedMethodIndex;
    }

    public void setSelectedMethodIndex(int selectedIndex) {
        this.selectedMethodIndex = selectedIndex;
    }

    public void setHint(String hint) {
        this.hint = hint;
    }

    @Init
    public void init(){

        try {
            se=Sessions.getCurrent();
            stage=WebUtils.getRedirectStage(se);

            if (stage.equals(REAUTHENTICATE)){

                services=WebUtils.getServices(se);
                UserService usrService=services.getUserService();

                //TODO: this should not be the enabled methods for his organization but user's enabled methods so far?
                Set<CredentialType> enabledMethods=services.getAppConfig().getEnabledMethods();
                immediateRedir=enabledMethods.size()<2;     //This means enabled method == preferred cred type
                if (immediateRedir)  //No need to select anything, redirecting on method onViewCreated
                    setHint(Labels.getLabel("usr.reauthn.progress"));
                else{
                    //show in this page the method selector
                    setHint(Labels.getLabel("usr.method.choose"));

                    //get a model for list of methods
                    availMethods= UIModel.getCredentialList(enabledMethods);
                    CredentialType cdtype=usrService.getPreferredMethod(WebUtils.getUser(se));
                    selectedMethodIndex=availMethods.indexOf(UIModel.createPair(cdtype));
                }
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

    @Command
    public void onViewCreated(){

        try {
            if (stage.equals(REAUTHENTICATE) && immediateRedir) {
                OxdService oxdService=services.getOxdService();
                UserService usrService=services.getUserService();
                User user=WebUtils.getUser(se);

                CredentialType preferred=usrService.getPreferredMethod(user);
                Optional<String> acrOptional= Utils.stringOptional(preferred.getName());
                acrOptional=Utils.stringOptional(acrOptional.orElse(preferred.getAlternativeName()));

                WebUtils.setRedirectStage(se, RedirectStage.FINAL);

                if (acrOptional.isPresent())
                    //do second Authz Redirect
                    WebUtils.execRedirect(oxdService.getAuthzUrl(Collections.singletonList(acrOptional.get()), "login"));
                else
                    //In theory this branch should not be reached... (this is hit in the Initiator already)
                    WebUtils.execRedirect(user.isAdmin()? WebUtils.ADMIN_PAGE_URL : WebUtils.USER_PAGE_URL);
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

    public ListModelSet<Pair<CredentialType, String>> getAvailMethods() {
        return availMethods;
    }

    @Command
    public void reauthenticate(){

        try {
            CredentialType ct = availMethods.getElementAt(selectedMethodIndex).getX();
            logger.debug("Index {}, Method {}", selectedMethodIndex, ct);

            String acrs[]= ct.getAlternativeName()==null ? new String[]{ct.getName()} : new String[]{ct.getName(), ct.getAlternativeName()};

            WebUtils.setRedirectStage(se, RedirectStage.FINAL);
            //re-authenticate passing acr
            WebUtils.execRedirect(services.getOxdService().getAuthzUrl(Arrays.asList(acrs), "login"));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

}