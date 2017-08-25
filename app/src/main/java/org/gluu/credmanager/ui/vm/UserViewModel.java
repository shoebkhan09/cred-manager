package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.services.ServiceMashup;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.Sessions;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Messagebox;

import java.util.List;

/**
 * Created by jgomer on 2017-08-04.
 */
public class UserViewModel {

    final int FEEDBACK_DELAY_SUCC=1500;
    final int FEEDBACK_DELAY_ERR=3000;

    private Logger logger = LogManager.getLogger(getClass());

    ServiceMashup services;
    User user;
    List<RegisteredCredential> devices;

    @Init
    public void init() throws Exception{
        Session se= Sessions.getCurrent();
        services= WebUtils.getServices(se);
        user=WebUtils.getUser(se);
    }

    public User getUser() {
        return user;
    }

    public List<RegisteredCredential> getDevices() {
        return devices;
    }

    @Command
    public void logoutFromAuthzServer(){

        try {
            Executions.sendRedirect(services.getOxdService().getLogoutUrl());
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }

    }

    void showMessageUI(boolean success){
        showMessageUI(success, Labels.getLabel(success ? "general.operation_completed" : "general.error.general"));
    }

    void showMessageUI(boolean success, String msg){
        showMessageUI(success, msg, "middle_center");
    }

    void showMessageUI(boolean success, String msg, String position) {
        if (success)
            Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_INFO, null, position, FEEDBACK_DELAY_SUCC);
        else
            Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_WARNING, null, position, FEEDBACK_DELAY_ERR);
    }

    boolean mayTriggerResetPreference(CredentialType preference, List<RegisteredCredential> devices, CredentialType credt){
        return preference!=null && devices.size()==1 && preference.equals(credt);
    }

    Pair<String, String> getDelMessages(boolean flag, CredentialType credt, String nick){

        String title;
        StringBuffer text=new StringBuffer();

        if (flag) {
            title=Labels.getLabel("general.credentials." + credt);
            text.append(Labels.getLabel("usr.del_conflict_preference", new String[]{title}));
            text.append("\n\n"); ;
        }
        title=Labels.getLabel("usr.del_title");
        text.append(Labels.getLabel("usr.del_confirm", new String[]{nick==null ? Labels.getLabel("general.no_named") : nick}));
        if (flag)
            text.append("\n");

        return new Pair<>(title, text.toString());

    }

}