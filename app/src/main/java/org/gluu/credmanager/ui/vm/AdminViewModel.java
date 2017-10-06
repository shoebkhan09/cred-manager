package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.AdminService;
import org.xdi.ldap.model.CustomAttribute;
import org.xdi.ldap.model.SimpleUser;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.BindingParam;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.Init;
import org.zkoss.bind.annotation.NotifyChange;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.util.Clients;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by jgomer on 2017-10-03.
 * This is the ViewModel of page admin.zul. It controls administrative functionalities
 */
public class AdminViewModel extends UserViewModel {

    private Logger logger = LogManager.getLogger(getClass());
    private AppConfiguration appCfg;

    private static final int MINLEN_SEARCH_PATTERN=3;

    private List<String> logLevels;
    private List<SimpleUser> users;
    private String selectedLogLevel;
    private String subpage;
    private String searchPattern;

    private AdminService myService;

    public String getSearchPattern() {
        return searchPattern;
    }

    public List<String> getLogLevels() {
        return logLevels;
    }

    public String getSelectedLogLevel() {
        return selectedLogLevel;
    }

    public String getSubpage() {
        return subpage;
    }

    public List<SimpleUser> getUsers() {
        return users;
    }

    public void setSearchPattern(String searchPattern) {
        this.searchPattern = searchPattern;
    }

    @Init(superclass = true)
    public void childInit() {
        myService=services.getAdminService();
        appCfg=services.getAppConfig();

        logLevels=Arrays.asList(Level.values()).stream().sorted().map(levl -> levl.name()).collect(Collectors.toList());
        selectedLogLevel=appCfg.getConfigSettings().getLogLevel();
    }

    @Command
    @NotifyChange({"subpage"})
    //Changes the page loaded in the content area (by default default.zul is being shown)
    public void loadSubPage(@BindingParam("page") String page){
        subpage=page;
        users=null;
    }

    @Command
    public void changeLogLevel(@BindingParam("level") String newLevel){
        //Here it is assumed that changing log level is always a successful operation
        appCfg.setLoggingLevel(newLevel);
        Clients.response(new AuInvoke("hideThrobber"));
        showMessageUI(true);
    }

    @Command
    public void search(@BindingParam("box") Component box){

        searchPattern=searchPattern.trim(); //avoid cheaters
        //Validates if input conforms to requirement of length
        if (Utils.stringOptional(searchPattern).map(str -> str.length()).orElse(0) < MINLEN_SEARCH_PATTERN)
            Clients.showNotification(Labels.getLabel("adm.resets_textbox_hint", new Integer[]{MINLEN_SEARCH_PATTERN}),
                    Clients.NOTIFICATION_TYPE_WARNING, box, "before_center", FEEDBACK_DELAY_ERR);
        else{
            users=myService.searchUsers(searchPattern);
            if (users==null)
                showMessageUI(false);
            else {
                //Takes the resulting list of users mathing the pattern and does sorting...
                users = users.stream().sorted(Comparator.comparing(SimpleUser::getUserId)).collect(Collectors.toList());

                //tricks: use a custom attribute to associate with checkboxes of the grid, and save dn elsewhere:
                for (SimpleUser u : users) {
                    List<CustomAttribute> custAttrs=u.getCustomAttributes();
                    custAttrs.add(new CustomAttribute("alreadyReset", "false"));
                    custAttrs.add(new CustomAttribute("realDN", u.getDn()));
                }

                //trick: use dn to associate with checkboxes of the grid:
                users.stream().forEach(user -> user.setDn("false"));
            }
            BindUtils.postNotifyChange(null, null, this, "users");
        }
        restoreUI();
    }

    @NotifyChange({"users"})
    @Command
    public void doReset(){

        //Pick those that haven't been reset before and that are checked in the grid currently
        List<String> userDNs=users.stream().filter(u -> u.getAttribute("alreadyReset").equals("false")
                && u.getDn().equals("true")).map(u -> u.getAttribute("realDN")).collect(Collectors.toList());

        if (userDNs.size()>0) { //proceed only if there is some fresh selection in the grid
            int total = myService.resetPreference(userDNs);
            if (total == userDNs.size()) {      //Check the no. of users changed matches the expected
                for (SimpleUser usr : users) {
                    CustomAttribute resetAttr = usr.getCustomAttributes().stream().
                            filter(attr -> attr.getName().equals("alreadyReset")).findFirst().get();
                    //attribute alreadyReset is true if the corresponding user row was selected
                    resetAttr.setValue(usr.getDn());    //DN here stores true/false not a DN!
                }
                showMessageUI(true);
            }
            else {
                //Flush list if something went wrong
                users = null;
                String msg = Labels.getLabel("adm.resets_entries_updated", new Integer[]{total});
                showMessageUI(false, Labels.getLabel("general.error.detailed", new String[]{msg}));
            }
        }
        restoreUI();

    }

    private void restoreUI(){
        Clients.response(new AuInvoke("hideThrobber"));
    }

    @NotifyChange({"users","searchPattern"})
    @Command
    public void cancel(){
        users=null;
        searchPattern=null;
    }

}