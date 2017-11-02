package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.conf.jsonized.LdapSettings;
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
 * This is the ViewModel of page admin.zul. It controls administrative functionalities. The method getConfigSettings in
 * AdminService bean is used to obtain (partial) references to the current working settings. The objects retrieved by
 * that method must not be changed (excluding primitives or Strings which if altered, do not modify the original object)
 */
public class AdminViewModel extends UserViewModel {

    private Logger logger = LogManager.getLogger(getClass());

    private static final int MINLEN_SEARCH_PATTERN=3;

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
    private LdapSettings ldapSettings;
    private boolean passResetEnabled;
    private boolean passResetImpossible;
    private int minCreds2FA;
    private List<Integer> minCredsList;

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
        return myService.getEnabledMethods();
    }

    public LdapSettings getLdapSettings() {
        return ldapSettings;
    }

    public void setLdapSettings(LdapSettings ldapSettings) {
        this.ldapSettings = ldapSettings;
    }

    public boolean isPassResetEnabled() {
        return passResetEnabled;
    }

    public boolean isPassResetImpossible() {
        return passResetImpossible;
    }

    public int getMinCreds2FA() {
        return minCreds2FA;
    }

    public void setMinCreds2FA(int minCreds2FA) {
        this.minCreds2FA = minCreds2FA;
    }

    public List<Integer> getMinCredsList() {
        return minCredsList;
    }

    @Init(superclass = true)
    public void childInit() {
        myService=services.getAdminService();

        //This is a fixed list that remains constant all the time
        logLevels=Arrays.asList(Level.values()).stream().sorted().map(levl -> levl.name()).collect(Collectors.toList());
    }

    @Command
    @NotifyChange({"subpage"})
    /**
     * Changes the page loaded in the content area. Also sets values needed in the UI (these are taken directly from
     * calls to AdminService's getConfigSettings method.
     * @param page The (string) url of the page that must be loaded (by default /admin/default.zul is being shown)
     */
    public void loadSubPage(@BindingParam("page") String page){
        subpage=page;
        users=null;
        reloadSettings();
        //Saves some computations
        if (page.equals("/admin/methods.zul"))
            initMethods();
    }

    public void reloadSettings(){
        selectedLogLevel=myService.getConfigSettings().getLogLevel();
        brandingPath=myService.getConfigSettings().getBrandingPath();
        passResetEnabled=myService.getConfigSettings().isEnablePassReset();
        passResetImpossible=myService.isPassResetImpossible();
        initOxd();
        initLdap();
        initMinCreds();
    }

    private void restoreUI(){
        Clients.response(new AuInvoke("hideThrobber"));
    }

    /* ========== LOG LEVEL ========== */

    @Command
    public void changeLogLevel(@BindingParam("level") String newLevel){

        String msg=myService.updateLoggingLevel(newLevel);
        if (msg==null)
            showMessageUI(true);
        else
            Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
        restoreUI();
    }

    /* ========== RESET CREDENTIALS ========== */

    @Command
    public void search(@BindingParam("box") Component box){

        //Validates if input conforms to requirement of length
        if (Utils.stringOptional(searchPattern).map(str -> str.trim().length()).orElse(0) < MINLEN_SEARCH_PATTERN)
            Clients.showNotification(Labels.getLabel("adm.resets_textbox_hint", new Integer[]{MINLEN_SEARCH_PATTERN}),
                    Clients.NOTIFICATION_TYPE_WARNING, box, "before_center", FEEDBACK_DELAY_ERR);
        else{
            users=myService.searchUsers(searchPattern.trim()); //avoid cheaters by trimming
            if (users==null)    //No resuls found
                showMessageUI(false);
            else {
                //Takes the resulting list of users matching the pattern and does sorting...
                users = users.stream().sorted(Comparator.comparing(SimpleUser::getUserId)).collect(Collectors.toList());

                //tricks: use a custom attribute to associate with checkboxes of the grid, and save dn elsewhere:
                for (SimpleUser u : users) {
                    List<CustomAttribute> custAttrs=u.getCustomAttributes();
                    custAttrs.add(new CustomAttribute("alreadyReset", "false"));
                    custAttrs.add(new CustomAttribute("realDN", u.getDn()));
                }

                //One more trick: use dn to associate with checkboxes of the grid:
                users.stream().forEach(user -> user.setDn("false"));
            }
            //triggers update of interface
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
            //Perform the actual resetting
            int total = myService.resetPreference(userDNs);
            if (total == userDNs.size()) {      //Check the no. of users changed matches the expected
                for (SimpleUser usr : users) {
                    //No NPE here because search method fills the alreadyReset attribute for all users in the grid
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
                String msg = Labels.getLabel("adm.resets_only_updated", new Integer[]{total});
                showMessageUI(false, Labels.getLabel("general.error.detailed", new String[]{msg}));
            }
        }
        else
            showMessageUI(false, Labels.getLabel("adm.resets_noselection"));
        restoreUI();

    }

    @Command
    //This simulates a click on a checkbox (although the click is coming from one made upon a row)
    public void rowClicked(@BindingParam("evt") Event event, @BindingParam("val") SimpleUser user){
        try {
            //IMPORTANT: Assuming the check is the first child of row!
            Checkbox box = (Checkbox) event.getTarget().getFirstChild();
            if (!box.isDisabled()) {
                //Simulate check on the checkbox
                box.setChecked(!box.isChecked());
                //Sync the user paired to this checkbox
                user.setDn(Boolean.toString(box.isChecked()));
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

    @NotifyChange({"users","searchPattern"})
    @Command
    public void cancelReset(){
        //Provoke the grid to disappear, and cleaning the search textbox
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
            storeBrandingPath();    //Apply update immediately when the default option has been selected
        }
        else
        if (brandingPath==null)
            brandingPath=value;     //Makes the textfield appear emptied, but no update takes place yet (see saveBrandingPath)
    }

    public void storeBrandingPath(){
        String msg=myService.updateBrandingPath(brandingPath);
        if (msg==null)
            showMessageUI(true);
        else
            Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
    }

    @Command
    public void saveBrandingPath(){

        try {
            //First predicate is required because isDirectory returns true if an empty path is provided ...
            if (Utils.stringOptional(brandingPath).isPresent() && Files.isDirectory(Paths.get(brandingPath))) {
                //Check directory exists
                if (!Files.isDirectory(Paths.get(brandingPath, "images")) || !Files.isDirectory(Paths.get(brandingPath, "styles")))
                    Messagebox.show(Labels.getLabel("adm.branding_no_subdirs"), null, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                            event -> {
                                if (Messagebox.ON_YES.equals(event.getName()))
                                    storeBrandingPath();
                            }
                    );
                else
                    storeBrandingPath();
            }
            else
                Messagebox.show(Labels.getLabel("adm.branding_no_dir"), null, Messagebox.OK, Messagebox.INFORMATION);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            Messagebox.show(Labels.getLabel("adm.branding_no_dir"), null, Messagebox.OK, Messagebox.INFORMATION);
        }
        restoreUI();

    }

    /* ========== OXD SETTINGS ========== */

    private void initOxd(){
        //Take immutable copy of oxd relevant settings
        oxdHost=myService.getConfigSettings().getOxdConfig().getHost();
        oxdPort=myService.getConfigSettings().getOxdConfig().getPort();
    }

    public void storeOxdSettings(){
        String msg=myService.updateOxdSettings(oxdHost, oxdPort);
        if (msg==null)
            Messagebox.show(Labels.getLabel("adm.restart_required"), null, Messagebox.OK, Messagebox.INFORMATION);
        else
            Messagebox.show(Labels.getLabel("adm.oxd_fail_update"), null, Messagebox.OK, Messagebox.EXCLAMATION);
    }

    @Command
    public void saveOxdSettings(){

        if (Utils.stringOptional(oxdHost).isPresent() && oxdPort>=0) {

            boolean connected=false;    //Try to guess if this is really an oxd-server
            try {
                connected=WebUtils.hostAvailabilityCheck(new InetSocketAddress(oxdHost, oxdPort), 3500);
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
            }
            if (!connected)
                Messagebox.show(Labels.getLabel("adm.oxd_no_connection"), null, Messagebox.YES | Messagebox.NO, Messagebox.QUESTION,
                        event -> {
                            if (Messagebox.ON_YES.equals(event.getName()))
                                storeOxdSettings();
                            else {  //Revert to last known working (or accepted)
                                initOxd();
                                BindUtils.postNotifyChange(null, null, AdminViewModel.this, "oxdHost");
                                BindUtils.postNotifyChange(null, null, AdminViewModel.this, "oxdPort");
                            }
                        }
                );
            else
                storeOxdSettings();
        }
        else
            Messagebox.show(Labels.getLabel("adm.oxd_no_settings"), null, Messagebox.OK, Messagebox.INFORMATION);
        restoreUI();
    }

    /* ========== ENABLED AUTHN METHODS ========== */

    private void initMethods(){

        try {
            //Retrieve current active ACR values
            Set<String> serverAcrs = myService.retrieveServerAcrs();
            //Fill list with all possible credential types no matter if enabled
            methods = UIModel.getCredentialList(new LinkedHashSet<>(Arrays.asList(CredentialType.values())));

            //Fill map used in the UI to determine if some checkbox is disabled
            activeMethods = new HashMap<>();
            methods.stream().map(Pair::getX)
                    .forEach(ct -> activeMethods.put(ct, serverAcrs.contains(ct.getName()) || serverAcrs.contains(ct.getAlternativeName())));

            //uiSelectedMethods is a set that is being sync whenever the user selects/deselects a method in the UI
            //Here it is initialized to the have the same items as the global AppConfiguration enabledMethods member
            uiSelectedMethods=new HashSet<>();
            getEnabledMethods().stream().forEach(ct -> uiSelectedMethods.add(ct));
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            methods=new ListModelList<>();      //This is to provoke the subpage becoming useless
        }

    }

    @Command
    public void checkMethod(@BindingParam("cred") CredentialType cred, @BindingParam("evt") Event evt){
        Checkbox box=(Checkbox) evt.getTarget();
        //Add or remove from set depending on whether it's checked or not
        if (box.isChecked())
            uiSelectedMethods.add(cred);
        else
            uiSelectedMethods.remove(cred);
    }

    @NotifyChange("enabledMethods")
    @Command
    //This method changes the application level configuration for enabled methods
    public void saveMethods(){

        List<String> failed=new ArrayList<>();
        //Iterate over only those originally enabled on the server
        Stream<CredentialType> stream=activeMethods.entrySet().stream().filter(entry -> entry.getValue()).map(entry -> entry.getKey());
        for (CredentialType method : stream.collect(Collectors.toList())){
            if (uiSelectedMethods.contains(method))
                getEnabledMethods().add(method);
            else {
                //Check if it's allowed to uncheck this method
                if (myService.zeroPreferences(method))
                    getEnabledMethods().remove(method);
                else {
                    uiSelectedMethods.add(method);  //Turn it on again
                    failed.add(method.getUIName());
                }
            }
        }
        if (failed.size()==0) {
            //Do the update and show success/fail message
            String msg=myService.updateEnabledMethods();
            if (msg==null)
                Messagebox.show(Labels.getLabel("adm.methods_change_success"), null, Messagebox.OK, Messagebox.INFORMATION);
            else
                Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
        }
        else
            Messagebox.show(Labels.getLabel("adm.methods_existing_credentials", new String[]{failed.toString()}),
                    null, Messagebox.OK, Messagebox.EXCLAMATION);

        restoreUI();

    }

    /* ========== LDAP SETTINGS ========== */

    public void initLdap(){
        //Retrieves a clone of the LDAP settings. This is so as the ldapSettings variable is bound to UI components,
        //so user interaction would affect the real operating settings if this weren't a clone
        ldapSettings=myService.copyOfWorkingLdapSettings();
    }

    @Command
    public void saveLdapSettings(){

        //salt is optional, check the others were provided
        List<String> settingsList=Arrays.asList(ldapSettings.getApplianceInum(), ldapSettings.getOrgInum(), ldapSettings.getOxLdapLocation());
        boolean nonNull=settingsList.stream().map(Utils::stringOptional).allMatch(Optional::isPresent);
        boolean nonEmpty=nonNull && settingsList.stream().allMatch(s -> s.trim().length()>0);

        if (nonNull && nonEmpty){
            //Verify values provided make sense
            String msg=myService.testLdapSettings(ldapSettings);
            if (msg==null) {
                //If they do, update
                msg=myService.updateLdapSettings();

                if (msg==null)
                    Messagebox.show(Labels.getLabel("adm.restart_required"), null, Messagebox.OK, Messagebox.INFORMATION);
                else
                    Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
            }
            else {
                initLdap();    //Revert to AdminService local (working) copy of settings
                Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
            }
            BindUtils.postNotifyChange(null, null, this, "ldapSettings");
        }
        else
            showMessageUI(false, Labels.getLabel("adm.ldap_nonempty"));
        restoreUI();

    }

    /* ========== PASS RESET ========== */

    @NotifyChange("passResetEnabled")
    @Command
    public void doSwitch(){
        passResetEnabled=!passResetEnabled;
        String msg=myService.updatePassReset(passResetEnabled);
        if (msg==null)
            showMessageUI(true);
        else
            Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
        restoreUI();
    }

    /* ========== MINIMUM CREDENTIALS FOR STRONG AUTHENTICATION ========== */

    public void initMinCreds(){

        minCreds2FA=myService.getConfigSettings().getMinCredsFor2FA();
        if (minCredsList==null) {
            minCredsList = new ArrayList<>();

            Pair<Integer, Integer> bounds = AppConfiguration.BOUNDS_MINCREDS_2FA;
            for (int i = bounds.getX(); i <= bounds.getY(); i++)
                minCredsList.add(i);
        }

    }

    public void storeMinCreds(int newval){

        String msg=myService.updateMinCreds(newval);
        if (msg==null)
            Messagebox.show(Labels.getLabel("adm.methods_change_success"), null, Messagebox.OK, Messagebox.INFORMATION);
        else
            Messagebox.show(msg, null, Messagebox.OK, Messagebox.EXCLAMATION);
        initMinCreds();

    }

    public void promptBeforeProceed(String message, int newval){

        Messagebox.show(message, null, Messagebox.YES | Messagebox.NO, Messagebox.EXCLAMATION,
                event -> {
                    if (Messagebox.ON_YES.equals(event.getName()))
                        storeMinCreds(newval);
                    else {  //Revert to last known working (or accepted)
                        initMinCreds();
                        BindUtils.postNotifyChange(null, null, AdminViewModel.this, "minCreds2FA");
                    }
                }
        );

    }

    @Command
    public void changeMinCreds(@BindingParam("val") Integer val){

        if (val==1)     //only one sucks
            promptBeforeProceed(Labels.getLabel("adm.strongauth_warning_one"), val);
        else
        if (val>minCreds2FA)   //maybe problematic...
            promptBeforeProceed(Labels.getLabel("adm.strongauth_warning_up", new Integer[]{minCreds2FA}), val);
        else
            storeMinCreds(val);
        restoreUI();

    }

}