package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
import org.gluu.credmanager.ui.model.UIModel;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.ExecutionArgParam;
import org.zkoss.bind.annotation.Init;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zul.ListModelSet;

import java.util.Optional;

/**
 * Created by jgomer on 2017-07-22.
 */
public class UserPreferenceViewModel {

    private String currentPreferred;
    private boolean editable;
    private boolean editing;
    private ListModelSet<Pair<CredentialType, String>> availMethods;
    private int selectedMethodIndex;
    private int prevSelectedMethodIndex;

    private UserService usrService;
    private User user;
    private Logger logger = LogManager.getLogger(getClass());

    @Init
    public void init(@ExecutionArgParam("user") User user) throws Exception{

        Session se=Sessions.getCurrent();
        ServiceMashup services=WebUtils.getServices(se);
        String noMethod=Labels.getLabel("usr.method.none");

        usrService=services.getUserService();
        this.user=user;
        CredentialType cdtype=user.getPreference();
        editable=user.getCredentials().size() >= AppConfiguration.ACTIVATE2AF_CREDS_GTE;
logger.debug("cred {}{}", user.getCredentials().size(), editable);
        Optional<String> optCred=Optional.ofNullable(cdtype).map(CredentialType::getUIName);
        currentPreferred=optCred.orElse(noMethod);

        availMethods = UIModel.getCredentialList(services.getAppConfig().getEnabledMethods());
        availMethods.add(new Pair<>(null, noMethod));
        selectedMethodIndex = (cdtype==null) ? availMethods.size()-1: availMethods.indexOf(UIModel.createPair(cdtype));
    }

    public String getCurrentPreferred() {
        return currentPreferred;
    }

    public boolean isEditing() {
        return editing;
    }

    public boolean isEditable() {
        return editable;
    }

    @Command
    public void cancelMethod(){
        editing=false;
        selectedMethodIndex=prevSelectedMethodIndex;
    }

    @Command
    public void updateMethod(){
        try {
            editing = false;
            user.setPreference(availMethods.getElementAt(selectedMethodIndex).getX());
            //TODO: save to ldap...
            //usrService.setPreferredMethod(user, );
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            //TODO: add error to messages
        }
    }

    @Command
    public void prepareUpdateMethod(){
        prevSelectedMethodIndex=selectedMethodIndex;
        editing=true;
    }

}