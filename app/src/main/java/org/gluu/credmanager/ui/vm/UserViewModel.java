package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.credential.RegisteredCredential;
import org.gluu.credmanager.core.credential.FidoDevice;
import org.gluu.credmanager.core.credential.SecurityKey;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.gluu.credmanager.conf.CredentialType.*;

/**
 * Created by jgomer on 2017-08-04.
 * This is the superclass of all ViewModels associated to zul pages used by regular users of the application
 */
public class UserViewModel {

    final static int FEEDBACK_DELAY_SUCC=1500;
    final static int FEEDBACK_DELAY_ERR=3000;

    private Logger logger = LogManager.getLogger(getClass());

    ServiceMashup services;
    UserService userService;
    User user;
    List<RegisteredCredential> devices;
    private Map<String,List<RegisteredCredential>> devicesMap;

    public User getUser() {
        return user;
    }

    public Map<String,List<RegisteredCredential>> getDevicesMap() {
        return devicesMap;
    }

    public List<RegisteredCredential> getDevices() {
        return devices;
    }

    @Init
    public void init() throws Exception{
        Session se= Sessions.getCurrent();
        services= WebUtils.getServices(se);
        user=WebUtils.getUser(se);
        userService=services.getUserService();

        devicesMap =new HashMap<>();
        user.getCredentials().entrySet().forEach(entry -> devicesMap.put(entry.getKey().toString(), entry.getValue()));
    }

    @Command
    public void logoutFromAuthzServer(){
        try {
            Session se= Sessions.getCurrent();
            String idToken=WebUtils.getIdToken(se);
            Executions.sendRedirect(services.getOxdService().getLogoutUrl(idToken));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }

    }

    static void showMessageUI(boolean success){
        showMessageUI(success, Labels.getLabel(success ? "general.operation_completed" : "general.error.general"));
    }

    static void showMessageUI(boolean success, String msg){
        showMessageUI(success, msg, "middle_center");
    }

    static void showMessageUI(boolean success, String msg, String position) {
        if (success)
            Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_INFO, null, position, FEEDBACK_DELAY_SUCC);
        else
            Clients.showNotification(msg, Clients.NOTIFICATION_TYPE_WARNING, null, position, FEEDBACK_DELAY_ERR);
    }

    //Second factor authentication will be available to users having at least this number of enrolled creds. Do not change visibility (it's used by .zul file)
    public int getMinimumCredsFor2FA(){     //this is public since it's called from zul templates
        return services.getAppConfig().getConfigSettings().getMinCredsFor2FA();
    }

    boolean mayTriggerResetPreference(CredentialType credType, int nCredsOfType){
        int total=devicesMap.values().stream().mapToInt(List::size).sum();
        return (total==getMinimumCredsFor2FA() && user.getPreference()!=null)
                || (nCredsOfType==1 && credType.equals(user.getPreference()));
    }

    Pair<String, String> getDelMessages(boolean flag, String nick){

        String title;
        StringBuilder text=new StringBuilder();

        if (flag) {
            text.append(Labels.getLabel("usr.del_conflict_preference", new Object[]{getMinimumCredsFor2FA()}));
            text.append("\n\n");
        }
        title=Labels.getLabel("usr.del_title");
        text.append(Labels.getLabel("usr.del_confirm", new String[]{nick==null ? Labels.getLabel("general.no_named") : nick}));
        if (flag)
            text.append("\n");

        return new Pair<>(title, text.toString());

    }

    void processFidoDeviceRemoval(FidoDevice device, int nCreds, Object bean){

        CredentialType type=device.getClass().equals(SecurityKey.class) ? SECURITY_KEY : SUPER_GLUU;
        boolean flag=mayTriggerResetPreference(type, nCreds);
        Pair<String, String> delMessages=getDelMessages(flag, device.getNickName());

        Messagebox.show(delMessages.getY(), delMessages.getX(), Messagebox.YES | Messagebox.NO, flag ? Messagebox.EXCLAMATION : Messagebox.QUESTION,
                event -> {
                    if (Messagebox.ON_YES.equals(event.getName())) {
                        try {
                            if (devices.remove(device)) {
                                if (flag)
                                    userService.setPreferredMethod(user,null);

                                userService.removeFidoDevice(device);
                                //trigger refresh (this method is asynchronous...)
                                BindUtils.postNotifyChange(null, null, bean, "devices");
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