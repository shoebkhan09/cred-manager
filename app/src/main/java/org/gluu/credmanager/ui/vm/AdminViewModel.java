package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.AdminService;
import org.gluu.credmanager.ui.model.UIModel;
import org.xdi.ldap.model.CustomAttribute;
import org.xdi.ldap.model.SimpleUser;
import org.zkoss.bind.BindUtils;
import org.zkoss.bind.annotation.*;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zul.Checkbox;
import org.zkoss.zul.ListModelList;
import org.zkoss.zul.Messagebox;

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-10-03.
 * This is the ViewModel of page admin.zul. It controls administrative functionalities
 */
public class AdminViewModel extends UserViewModel {

    private Logger logger = LogManager.getLogger(getClass());
    private AppConfiguration appCfg;

    private static final int MINLEN_SEARCH_PATTERN=3;

    private String appName;
    private List<String> logLevels;
    private List<SimpleUser> users;
    private String selectedLogLevel;
    private String subpage;
    private String searchPattern;
    private String brandingPath;
    private String oxdHost;
    private int oxdPort;
    private ListModelList<Pair<CredentialType, String>> methods;
    private Map<CredentialType, Boolean> activeMethods;
    private Set<CredentialType> uiSelectedMethods;

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

    @DependsOn("brandingPath")
    public boolean isCustomBrandingEnabled() {
        return brandingPath!=null;
    }

    public String getBrandingPath() {
        return brandingPath;
    }

    public void setBrandingPath(String brandingPath) {
        this.brandingPath = brandingPath;
    }

    public String getOxdHost() {
        return oxdHost;
    }

    public void setOxdHost(String oxdHost) {
        this.oxdHost = oxdHost;
    }

    public int getOxdPort() {
        return oxdPort;
    }

    public void setOxdPort(int oxdPort) {
        this.oxdPort = oxdPort;
    }

    public ListModelList<Pair<CredentialType, String>> getMethods() {
        return methods;
    }

    public Map<CredentialType, Boolean> getActiveMethods() {
        return activeMethods;
    }

    public Set<CredentialType> getEnabledMethods(){
        return appCfg.getEnabledMethods();
    }

    @Init(superclass = true)
    public void childInit() {
        appName=Executions.getCurrent().getDesktop().getWebApp().getAppName();
        myService=services.getAdminService();
        appCfg=services.getAppConfig();

        logLevels=Arrays.asList(Level.values()).stream().sorted().map(levl -> levl.name()).collect(Collectors.toList());
        selectedLogLevel=appCfg.getConfigSettings().getLogLevel();
        brandingPath=appCfg.getConfigSettings().getBrandingPath();
        oxdHost=appCfg.getConfigSettings().getOxdConfig().getHost();
        oxdPort=appCfg.getConfigSettings().getOxdConfig().getPort();

        initMethods();
    }

    @Command
    @NotifyChange({"subpage"})
    //Changes the page loaded in the content area (by default default.zul is being shown)
    public void loadSubPage(@BindingParam("page") String page){
        subpage=page;
        users=null;
    }

    private void restoreUI(){
        Clients.response(new AuInvoke("hideThrobber"));
    }

    /* ========== LOG LEVEL ========== */

    @Command
    public void changeLogLevel(@BindingParam("level") String newLevel){
        //Here it is assumed that changing log level is always a successful operation
        appCfg.setLoggingLevel(newLevel);
        Clients.response(new AuInvoke("hideThrobber"));
        showMessageUI(true);
        myService.logAdminEvent("Log level changed to " + newLevel);
    }

    /* ========== RESET CREDENTIALS ========== */

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
                myService.logAdminEvent("Reset preferred method for users " + users.stream().map(u -> u.getUserId()).collect(Collectors.toList()));
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

    @NotifyChange({"users","searchPattern"})
    @Command
    public void cancelReset(){
        users=null;
        searchPattern=null;
    }

    /* ========== CUSTOM BRANDING ========== */

    @NotifyChange("brandingPath")
    @Command
    public void changeBranding(@BindingParam("val") String value){
        //A non-null value means custom branding is selected
        if (value==null) {
            brandingPath = null;
            storeBrandingPath();
        }
        else
        if (brandingPath==null)
            brandingPath=value;
    }

    public void storeBrandingPath(){
        appCfg.getConfigSettings().setBrandingPath(brandingPath);
        showMessageUI(true);
        myService.logAdminEvent("Changed branding path to " + brandingPath);
    }

    @Command
    public void saveBrandingPath(){
        //Check directory exists

        try {
            //First predicate is required because isDirectory returns true if an empty path is provided ...
            if (Utils.stringOptional(brandingPath).isPresent() && Files.isDirectory(Paths.get(brandingPath))) {
                if (!Files.isDirectory(Paths.get(brandingPath, "images")) || !Files.isDirectory(Paths.get(brandingPath, "styles")))
                    Messagebox.show(Labels.getLabel("adm.branding_no_subdirs"), appName, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                            event -> {
                                if (Messagebox.ON_YES.equals(event.getName()))
                                    storeBrandingPath();
                            }
                    );
                else
                    storeBrandingPath();
            }
            else
                Messagebox.show(Labels.getLabel("adm.branding_no_dir"), appName, Messagebox.OK, Messagebox.INFORMATION);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            Messagebox.show(Labels.getLabel("adm.branding_no_dir"), appName, Messagebox.OK, Messagebox.INFORMATION);
        }
        restoreUI();

    }

    /* ========== OXD SETTINGS ========== */

    public void storeOxdSettings(){
        appCfg.getConfigSettings().getOxdConfig().setHost(oxdHost);
        appCfg.getConfigSettings().getOxdConfig().setPort(oxdPort);
        appCfg.getConfigSettings().getOxdConfig().setOxdId(null);   //This will provoke re-registration
        Messagebox.show(Labels.getLabel("adm.oxd_restart_required"), appName, Messagebox.OK, Messagebox.INFORMATION);
        myService.logAdminEvent("Changed oxd host/port to " + oxdHost + "/" + oxdPort);
    }

    @Command
    public void saveOxdSettings(){

        if (Utils.stringOptional(oxdHost).isPresent() && oxdPort>=0) {

            boolean connected=false;
            try {
                connected=WebUtils.hostAvailabilityCheck(new InetSocketAddress(oxdHost, oxdPort), 3500);
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
            }
            if (!connected)
                Messagebox.show(Labels.getLabel("adm.oxd_no_connection"), appName, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                        event -> {
                            if (Messagebox.ON_YES.equals(event.getName()))
                                storeOxdSettings();
                        }
                );
            else
                storeOxdSettings();
        }
        else
            Messagebox.show(Labels.getLabel("adm.oxd_no_settings"), appName, Messagebox.OK, Messagebox.INFORMATION);
        restoreUI();
    }

    /* ========== ENABLED AUTHN METHODS ========== */

    private void initMethods(){

        try {
            Set<String> serverAcrs = appCfg.retrieveServerAcrs();
            //This one contains all possible credential types no matter if enabled
            methods = UIModel.getCredentialList(new LinkedHashSet<>(Arrays.asList(CredentialType.values())));

            activeMethods = new HashMap<>();
            methods.stream().map(Pair::getX)
                    .forEach(ct -> activeMethods.put(ct, serverAcrs.contains(ct.getName()) || serverAcrs.contains(ct.getAlternativeName())));

            //Copy global (AppConfiguration's) enabled methods
            uiSelectedMethods=new HashSet<>();
            getEnabledMethods().stream().forEach(ct -> uiSelectedMethods.add(ct));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }

    }

    @Command
    public void checkMethod(@BindingParam("cred") CredentialType cred, @BindingParam("evt") Event evt){
        Checkbox box=(Checkbox) evt.getTarget();
        if (box.isChecked())
            uiSelectedMethods.add(cred);
        else
            uiSelectedMethods.remove(cred);
    }

    @NotifyChange("enabledMethods")
    @Command
    public void saveMethods(){

        List<String> failed=new ArrayList<>();
        //Revise only that are originally enabled on the server
        Stream<CredentialType> stream=activeMethods.entrySet().stream().filter(entry -> entry.getValue()).map(entry -> entry.getKey());
        for (CredentialType method : stream.collect(Collectors.toList())){
            if (uiSelectedMethods.contains(method))
                getEnabledMethods().add(method);
            else {
                if (myService.zeroPreferences(method))
                    getEnabledMethods().remove(method);
                else {
                    uiSelectedMethods.add(method);  //Turn it on again
                    failed.add(method.getUIName());
                }
            }
        }
        if (failed.size()==0)
            Messagebox.show(Labels.getLabel("adm.methods_change_success"), appName, Messagebox.OK, Messagebox.INFORMATION);
        else
            Messagebox.show(Labels.getLabel("adm.methods_existing_credentials", new String[]{failed.toString()}),
                    appName, Messagebox.OK, Messagebox.EXCLAMATION);

        restoreUI();

    }

}