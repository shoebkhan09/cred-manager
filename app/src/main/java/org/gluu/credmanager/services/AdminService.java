/*
 * cred-manager is available under the MIT License 2008. See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright c 2017, Gluu
 */
package org.gluu.credmanager.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.ComputedOxdSettings;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.conf.jsonized.Configs;
import org.gluu.credmanager.conf.jsonized.LdapSettings;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.xdi.ldap.model.SimpleUser;
import org.zkoss.util.resource.Labels;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by jgomer on 2017-10-05.
 * A app-scoped bean that contains methods to achieve administrative tasks
 */
@ApplicationScoped
public class AdminService {

    private Logger logger = LogManager.getLogger(getClass());

    @Inject
    private AppConfiguration appConfig;

    @Inject
    private OxdService oxdService;

    @Inject
    private LdapService ldapService;

    private Configs localSettings;

    private ObjectMapper mapper;

    public Configs getConfigSettings(){
        return localSettings;
    }

    @PostConstruct
    public void setup(){
        try {
            //ways to clone-deep a bean?
            localSettings = (Configs) BeanUtils.cloneBean(appConfig.getConfigSettings());
            //mapper=new ObjectMapper();
            //localSettings = mapper.convertValue(appConfig.getConfigSettings(), Configs.class);
            //localSettings=mapper.readValue(mapper.writeValueAsString(appConfig.getConfigSettings()), new TypeReference<Configs>(){});
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
    }

    public void logAdminEvent(String description){
        logger.warn(Labels.getLabel("adm.admin_event"), description);
    }

    /* ========== "PROXY" METHODS ========== */

    public Set<String> retrieveServerAcrs() throws Exception {
        return appConfig.retrieveServerAcrs();
    }

    public Set<CredentialType> getEnabledMethods(){
        return appConfig.getEnabledMethods();
    }

    private String updateSettings(){

        String detail=null;
        try {
            appConfig.updateConfigFile(localSettings);
        }
        catch (Exception e){
            detail=Labels.getLabel("adm.conffile_error_update");
            logger.error(e.getMessage(), e);
        }
        return detail;

    }

    /* ========== RESET CREDENTIALS ========== */

    /**
     * Builds a list of users whose username, first or last name matches the pattern passed, and at the same time have a
     * preferred authentication method other than password
     * @param str Pattern for search
     * @return A collection of SimpleUser instances. Null if an error occurred to compute the list
     */
    public List<SimpleUser> searchUsers(String str) {

        List<SimpleUser> list=null;
        try {
            list = ldapService.findUsersWithPreferred(str, new String[]{"uid", "givenName", "sn"}, SimpleUser.class);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return list;
    }

    /**
     * Resets the preferred method of authentication for the users referenced by LDAP dn
     * @param userDNs A List containing user DNs
     * @return The number of modified entries in LDAP
     */
    public int resetPreference(List<String> userDNs) {

        int modified=0;
        try{
            for (String dn : userDNs){
                ldapService.updatePreferredMethod(dn.substring(0, dn.indexOf(",")).trim(), null);
                modified++;
                logAdminEvent("Reset preferred method for user " + dn);
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return modified;

    }

    /**
     * Determines if there are no users with this type of method as preferred in LDAP
     * @param type A credential
     * @return False if any user has type as his preferred. True otherwise
     */
    public boolean zeroPreferences(CredentialType type){

        boolean zero;
        try {
            List<String> acrs=new ArrayList<>();
            acrs.add(type.getName());
            if (type.getAlternativeName()!=null)
                acrs.add(type.getAlternativeName());

            zero=ldapService.preferenceCount(acrs)==0;
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            zero=false;
        }
        return zero;

    }

    /* ========== LOG LEVEL ========== */

    public String updateLoggingLevel(String level){
        localSettings.setLogLevel(level);
        //Do runtime change (here it is assumed that changing log level is always a successful operation)
        appConfig.setLoggingLevel(level);
        logAdminEvent("Log level changed to " + level);
        //persist
        return updateSettings();
    }

    /* ========== CUSTOM BRANDING ========== */

    public String updateBrandingPath(String path){
        path=Utils.stringOptional(path).orElse(null);   //converts empty or null in null, otherwise leave it intact
        localSettings.setBrandingPath(path);
        //Do runtime change
        appConfig.getConfigSettings().setBrandingPath(path);
        logAdminEvent("Changed branding path to " + path);
        //persist
        return updateSettings();
    }

    /* ========== OXD SETTINGS ========== */

    public ComputedOxdSettings getComputedOxdSettings(){
        return oxdService.getComputedSettings();
    }

    public OxdConfig copyOfWorkingOxdSettings(){
        return copyOfOxdSettings(localSettings.getOxdConfig());
    }

    private OxdConfig copyOfOxdSettings(OxdConfig settings){

        OxdConfig oxdSettings=null;
        try {
            oxdSettings=(OxdConfig) BeanUtils.cloneBean(settings);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return oxdSettings;

    }

    public String testOxdSettings(OxdConfig newSettings){

        String msg=null;
        OxdConfig backup=copyOfWorkingOxdSettings();
        OxdConfig newSettingsCopy=copyOfOxdSettings(newSettings);   //This prevents side effects
        try {
            oxdService.setSettings(newSettings);
            //If it gets here, it means the provided settings were fine, so local copy can be overwritten
            localSettings.setOxdConfig(newSettingsCopy);
        }
        catch (Exception e){
            msg=e.getMessage();
            try {
                logger.warn(Labels.getLabel("adm.oxd_revert_conf"));
                //Revert to last working settings
                oxdService.setSettings(backup);
            }
            catch (Exception e1){
                msg=Labels.getLabel("admin.error_reverting");
                logger.error(e1.getMessage(), e1);
            }
        }
        return msg;

    }

    public String updateOxdSettings(){
        return updateSettings();
    }

    /* ========== LDAP SETTINGS ========== */

    public LdapSettings copyOfWorkingLdapSettings(){
        return copyOfLdapSettings(localSettings.getLdapSettings());
    }

    private LdapSettings copyOfLdapSettings(LdapSettings settings){

        LdapSettings ldapSettings=null;
        try {
            ldapSettings=(LdapSettings) BeanUtils.cloneBean(settings);
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
        }
        return ldapSettings;

    }

    //This method does not change application level settings
    public String testLdapSettings(LdapSettings newSettings){

        String msg=null;
        LdapSettings backup=copyOfWorkingLdapSettings();
        //Cloning here is critical, or else a change in the UI may distort the real LDAP settings
        LdapSettings newSettingsCopy=copyOfLdapSettings(newSettings);
        try{
            logger.info(Labels.getLabel("adm.ldap_testing"));
            ldapService.setup(newSettingsCopy);
            //If it gets here, it means the provided settings were fine, so local copy can be overwritten
            localSettings.setLdapSettings(newSettingsCopy);
        }
        catch (Exception e) {
            msg = e.getMessage();
        }
        try{
            //Revert to backup settings
            logger.warn(Labels.getLabel("adm.ldap_revert_conf"));
            ldapService.setup(backup);
        }
        catch (Exception e1){
            msg=Labels.getLabel("admin.error_reverting");
            logger.fatal(e1.getMessage(), e1);
        }
        return msg;

    }

    public String updateLdapSettings(){
        //persist
        return updateSettings();
    }

    /* ========== PASS RESET ========== */

    public boolean isPassResetImpossible(){
        try {
            return ldapService.isBackendLdapEnabled();
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            return true;
        }
    }

    public String updatePassReset(boolean val){
        //update local copy
        localSettings.setEnablePassReset(val);
        //Do runtime change
        appConfig.setPassReseteable(val);
        logAdminEvent("Changed pass reset availability to " + Boolean.toString(val).toUpperCase());
        //persist
        return updateSettings();
    }

    /* ========== ENABLED AUTHN METHODS ========== */

    public String updateEnabledMethods(){

        //The runtime variable used for storing the enabled methods is already updated
        Set<CredentialType> set=getEnabledMethods();
        logAdminEvent("Changed enabled methods to: " + set);
        List<String> list=set.stream().map(CredentialType::getName).collect(Collectors.toList());
        localSettings.setEnabledMethods(list);
        return updateSettings();

    }

    public boolean reloadMethodConfig(CredentialType cred){

        boolean success=false;
        switch (cred){
            case OTP:
                success=appConfig.computeOTPSettings(appConfig.getConfigSettings());
                break;
            case SECURITY_KEY:
                success=appConfig.computeU2fSettings(appConfig.getConfigSettings());
                break;
            case VERIFIED_PHONE:
                success=appConfig.computeTwilioSettings(appConfig.getConfigSettings());
                break;
            case SUPER_GLUU:
                success=appConfig.computeSuperGluuSettings(appConfig.getConfigSettings());
                break;
        }
        return success;

    }

    /* ========== MINIMUM CREDENTIALS FOR STRONG AUTHENTICATION ========== */

    public String updateMinCreds(int minCreds){

        //update local copy
        localSettings.setMinCredsFor2FA(minCreds);
        //Do runtime change
        appConfig.getConfigSettings().setMinCredsFor2FA(minCreds);
        logAdminEvent("Changed minimum number of enrolled credentials for 2FA usage to " + minCreds);

        return updateSettings();

    }

}
