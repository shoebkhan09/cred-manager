package org.gluu.credmanager.conf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.gluu.credmanager.conf.jsonized.Configs;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.gluu.credmanager.conf.jsonized.U2fSettings;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.OxdService;
import org.gluu.credmanager.services.ldap.pojo.CustomScript;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;

import static org.gluu.credmanager.conf.CredentialType.*;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Created by jgomer on 2017-07-04.
 * This managed bean is in charge of reading/parsing all necessary settings the application needs for working.
 * All configurations are taken primarly from the app's Json config file, and from LDAP as described in app's admin guide
 * The method setup (annotated with @PostConstruct) triggers all parsing. This class does its best to infer certain
 * parameters when they are not provided and warns whenever a vital parameter is wrong, missing or inconsistent (see
 * member inOperableState). Most configurations are saved in the configSettings member.
 */
@ApplicationScoped
public class AppConfiguration{

    private final String DEFAULT_GLUU_BASE="/etc/gluu";
    private final String CONF_FILE_RELATIVE_PATH="conf/cred-manager.json";
    private final String OXAUTH_WAR_LOCATION= "/opt/gluu/jetty/oxauth/webapps/oxauth.war";
    private final String DEFAULT_GLUU_VERSION="3.0.2";      //Current app version is mainly targeted at this version of Gluu Server

    public static final Pair<Integer, Integer> BOUNDS_MINCREDS_2FA =new Pair<>(1,3);
    public static final String BASE_URL_BRANDING_PATH="/custom";

    /*
     ACR value for the routing authentication script. WARNING!: this script has to be enabled in your Gluu server with
     the same or lower level than SIMPLE_AUTH_ACR
     */
    //TODO: look out!
    private static final String ROUTING_ACR="router";//"cred_manager";
    /*
     ACR value for user+password auth only. It is not necessarily equivalent to Gluu's default authn method which is found
     in the oxAuthenticationMode attribute of the appliance. Anyway, SIMPLE_AUTH_ACR should be part of server's acr_supported_values
     */
    private static final String SIMPLE_AUTH_ACR ="basic";   //"auth_ldap_server";

    //========== Properties exposed by this service ==========

    private Configs configSettings;
    private boolean inOperableState=false;  //Use pesimistic approach (assume it's likelier to fail than to succeed)
    private String orgName;
    private String issuerUrl;

    //The following override those properties found inside configSettings. Administrative functionalities (see AdminService bean),
    //MUST update these properties as well as those of Configs object (which are the ones that can be serialized to disk)
    private Set<CredentialType> enabledMethods;
    private boolean passReseteable;
    private String gluuVersion;
    //============================================================

    private Logger logger = LogManager.getLogger(getClass());
    private ObjectMapper mapper=new ObjectMapper();
    private String gluuBase;
    private File srcConfigFile;

    @Inject
    private LdapService ldapService;

    @Inject
    private OxdService oxdService;

    public String getRoutingAcr(){
        return ROUTING_ACR;
    }

    public String getOrgName() {
        return orgName;
    }

    public boolean isPassReseteable() {
        return passReseteable;
    }

    /*
    public String getGluuVersion() {
        return gluuVersion;
    }
    */

    public Set<CredentialType> getEnabledMethods() {
        return enabledMethods;
    }

    public boolean isInOperableState() {
        return inOperableState;
    }

    public Configs getConfigSettings() {
        return configSettings;
    }

    public String getIssuerUrl() {
        return issuerUrl;
    }

    public void setPassReseteable(boolean passReseteable) {
        this.passReseteable = passReseteable;
    }

    private void setGluuBase(String candidateGluuBase) {

        String osName = System.getProperty("os.name").toLowerCase();
        boolean windows = osName.matches(".*win.*");

        if (candidateGluuBase==null && !windows)
            gluuBase = DEFAULT_GLUU_BASE;
        else
            gluuBase = candidateGluuBase;

    }

    /**
     * Returns a reference to the configuration file of the application (cred-manager.json)
     * @param baseDir Path to configuration file without the CONF_FILE_RELATIVE_PATH part
     * @return A File object
     */
    private File getConfigFile(String baseDir){
        Path path=Paths.get(baseDir, CONF_FILE_RELATIVE_PATH);
        return Files.exists(path) ? path.toFile() : null;
    }

    public AppConfiguration() {}

    @PostConstruct
    public void setup(){
        setGluuBase(System.getProperty("gluu.base"));

        if (gluuBase!=null){
            //Get a reference to the config-file
            srcConfigFile=getConfigFile(gluuBase);
            if (srcConfigFile==null)
                logger.error(Labels.getLabel("app.conf_file_not_readable"), CONF_FILE_RELATIVE_PATH);
            else
                try{
                    //Parses config file in a Configs instance
                    configSettings=mapper.readValue(srcConfigFile, Configs.class);

                    //Check settings consistency, infer some, and override others
                    if (computeSettings(configSettings))
                        try {
                            updateConfigFile(configSettings);
                        }
                        catch (Exception e) {
                            logger.error(Labels.getLabel("app.conf_update_error"), e);
                        }
                }
                catch (Exception e){
                    inOperableState=false;
                    logger.error(Labels.getLabel("app.conf_file_not_parsable"));
                    logger.error(e.getMessage(),e);
                }
        }

    }

    public void updateConfigFile(Configs configs) throws Exception{
        //update file to disk
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        mapper.writeValue(srcConfigFile, configs);
    }

    private boolean computeSettings(Configs settings) {

        boolean update=false;
        Optional<String> oxLdapOpt=Utils.stringOptional(settings.getLdapSettings().getOxLdapLocation());

        if (oxLdapOpt.isPresent() && Files.exists(Paths.get(oxLdapOpt.get()))){

            Optional<String> applianceOpt=Utils.stringOptional(settings.getLdapSettings().getApplianceInum());
            Optional<String> orgIdOpt=Utils.stringOptional(settings.getLdapSettings().getOrgInum());

            if (applianceOpt.isPresent() && orgIdOpt.isPresent()) {
                try {
                    ldapService.setup(settings.getLdapSettings());
                }
                catch (Exception e){
                    logger.error(Labels.getLabel("app.err_init_ldapmanager"), e.getMessage());
                    logger.error(e.getMessage(), e);
                    ldapService=null;
                }

                if (ldapService!=null) {
                    try {
                        orgName = ldapService.getOrganizationName();
                        issuerUrl=ldapService.getIssuerUrl();
                        computeLoggingLevel(settings);
                        computeBrandingPath(settings);
                        computeGluuVersion(settings);
                        computeMinCredsForStrongAuth(settings);
                        computePassReseteable(settings, ldapService.isBackendLdapEnabled());
                        computeEnabledMethods(settings);
                        //The following 4 statements are executed only after computeEnabledMethods and computeGluuVersion are called
                        computeU2fSettings(settings);
                        computeOTPSettings(settings);
                        computeSuperGluuSettings(settings);
                        computeTwilioSettings(settings);

                        update=computeOxdSettings(settings);
                    }
                    catch (Exception e){
                        logger.error(e.getMessage(),e);
                    }
                }
            }
            else
                logger.error(Labels.getLabel("app.appliance_orgid_missing"));
        }
        else
            logger.error(Labels.getLabel("app.inexistent_ox-ldap"));

        return update;

    }

    private void computeGluuVersion(Configs settings){

        Optional<String> optGluu = Utils.stringOptional(settings.getGluuVersion());
        optGluu = Utils.stringOptional(optGluu.orElse(guessGluuVersion()));   //try guessing if necessary
        gluuVersion=optGluu.orElse(DEFAULT_GLUU_VERSION);      //use default if needed

    }

    private void computeMinCredsForStrongAuth(Configs settings){

        int defaultValue=(BOUNDS_MINCREDS_2FA.getX() + BOUNDS_MINCREDS_2FA.getY())/2;
        Integer providedValue=settings.getMinCredsFor2FA();
        if (providedValue==null) {
            logger.info(Labels.getLabel("app.mincreds_defaulted"));
            settings.setMinCredsFor2FA(defaultValue);
        }
        else
        if (providedValue < BOUNDS_MINCREDS_2FA.getX() || providedValue > BOUNDS_MINCREDS_2FA.getY()) {
            logger.info(Labels.getLabel("app.mincreds_2FA_notinbounds"), providedValue, BOUNDS_MINCREDS_2FA.getX(), BOUNDS_MINCREDS_2FA.getY(), defaultValue);
            settings.setMinCredsFor2FA(defaultValue);
        }
    }

    private void computeLoggingLevel(Configs configSettings){

        String currentLevl=getLoggingLevel().name();
        String levelInConfFile=configSettings.getLogLevel();

        if (levelInConfFile==null) {
            logger.info(Labels.getLabel("app.current_log_level"), currentLevl);
            configSettings.setLogLevel(currentLevl);
        }
        else
            try {
                Level.valueOf(levelInConfFile);
                logger.info(Labels.getLabel("app.set_log_level"), levelInConfFile);
                setLoggingLevel(levelInConfFile);
            }
            catch (Exception e) {
                logger.warn(Labels.getLabel("app.wrong_log_level"), levelInConfFile, currentLevl);
            }
    }

    private void computeBrandingPath(Configs settings){

        String path=settings.getBrandingPath();
        /*
        if (path!=null){
            boolean cond=path.endsWith(BASE_URL_BRANDING_PATH) || path.endsWith(File.separator + BASE_URL_BRANDING_PATH.substring(1));
            if (!(Files.isDirectory(Paths.get(path)) && cond )){
            }
        }
        */
        try{
            if (Utils.stringOptional(path).isPresent() && !Files.isDirectory(Paths.get(path)))
                throw new IOException("Not a directory");
        }
        catch (Exception e){
            logger.error(Labels.getLabel("app.wrong_branding_path"), path);
            logger.error(e.getMessage(), e);
            settings.setBrandingPath(null);
        }

    }

    private void computePassReseteable(Configs settings, boolean withBackend){

        passReseteable=settings.isEnablePassReset();
        if (settings.isEnablePassReset() && withBackend) {
            logger.error(Labels.getLabel("app.pass_reset_turnedoff"));
            passReseteable=false;
        }

    }

    private void computeOTPSettings(Configs settings) throws Exception{

        if (enabledMethods.contains(OTP)) {
            CustomScript script=ldapService.getCustomScript(OTP.getName());
            OTPConfig config=OTPConfig.get(script);
            if (config==null) {
                enabledMethods.remove(OTP);
                logger.error(Labels.getLabel("app.otp_settings_error"));
            }
            else
                settings.setOtpConfig(config);
        }

    }

    private void computeSuperGluuSettings(Configs settings) throws Exception{

        if (enabledMethods.contains(SUPER_GLUU)){
            CustomScript script=ldapService.getCustomScript(SUPER_GLUU.getName());
            SGConfig config=SGConfig.get(script);
            if (config==null){
                enabledMethods.remove(SUPER_GLUU);
                logger.error(Labels.getLabel("app.sg_settings_error"));
            }
            else
                settings.setSgConfig(config);
        }

    }

    private void computeTwilioSettings(Configs settings) throws Exception{
        if (enabledMethods.contains(VERIFIED_PHONE)){
            CustomScript script=ldapService.getCustomScript(VERIFIED_PHONE.getName());
            TwilioConfig config=TwilioConfig.get(script);
            if (config==null){
                enabledMethods.remove(VERIFIED_PHONE);
                logger.error(Labels.getLabel("app.sms_settings_error"));
            }
            else
                settings.setTwilioConfig(config);
        }
    }

    private void computeU2fSettings(Configs settings) {

        if (enabledMethods.contains(SECURITY_KEY))
            try {
                U2fSettings u2fCfg = settings.getU2fSettings();
                boolean nocfg = u2fCfg == null;
                boolean guessAppId = nocfg || u2fCfg.getAppId() == null;
                boolean guessUri = nocfg || u2fCfg.getRelativeMetadataUri() == null;

                if (nocfg)
                    u2fCfg = new U2fSettings();

                if (guessAppId) {
                    u2fCfg.setAppId(issuerUrl);
                    logger.warn(Labels.getLabel("app.metadata_guessed"), "U2F app ID", issuerUrl);
                }

                String endpointUrl = u2fCfg.getRelativeMetadataUri();
                if (guessUri) {
                    endpointUrl = ".well-known/fido-u2f-configuration";

                    u2fCfg.setRelativeMetadataUri(endpointUrl);
                    logger.warn(Labels.getLabel("app.metadata_guessed"), "U2F relative endpoint URL", endpointUrl);
                }
                u2fCfg.setEndpointUrl(String.format("%s/%s", issuerUrl, endpointUrl));

                settings.setU2fSettings(u2fCfg);
                logger.info(Labels.getLabel("app.u2f_settings"), mapper.writeValueAsString(u2fCfg));
            }
            catch (Exception e){
                enabledMethods.remove(SECURITY_KEY);
                logger.error(Labels.getLabel("app.u2f_settings_error"));
            }

    }

    /**
     * Performs a GET to the OIDC metadata URL and extracts the ACR values supported by the server
     * @return A Set of String values
     * @throws Exception If an networking or parsing error occurs
     */
    public Set<String> retrieveServerAcrs() throws Exception{

        String OIDCEndpointURL=ldapService.getOIDCEndpoint();
        logger.debug(Labels.getLabel("app.obtaining_acrs"), OIDCEndpointURL);
        JsonNode values=mapper.readTree(new URL(OIDCEndpointURL)).get("acr_values_supported");

        //Store server's supported acr values in a set
        Set<String> supportedSet=new HashSet<>();
        values.forEach(node -> supportedSet.add(node.asText()));

        return supportedSet;

    }

    private void computeEnabledMethods(Configs settings) throws Exception{

        Set<String> possibleMethods=new HashSet<>(CredentialType.ACR_NAMES_SUPPORTED);
        Set<String> supportedSet=retrieveServerAcrs();

        //Verify default and routing acr are there
        List<String> acrList=Arrays.asList(SIMPLE_AUTH_ACR, ROUTING_ACR);
        if (supportedSet.containsAll(acrList)) {

            //Add them all to oxd configuration object. These will be a superset of methods used in practice...
            settings.getOxdConfig().setAcrValues(new HashSet<>(supportedSet));
            //Now, keep the interesting ones. This will filter things like "basic", "auth_ldap_server", etc.
            supportedSet.retainAll(possibleMethods);

            Optional<List<String>> methods=Utils.listOptional(settings.getEnabledMethods());

            //If there are no enabled methods in config file, assume we want all of them
            Set<String> enabledSet = new HashSet<>(methods.isPresent() ? methods.get() : possibleMethods);
            //From enabled methods in config file, keep only those really supported by server
            enabledSet.retainAll(supportedSet);

            //log result so far
            logger.warn(Labels.getLabel("app.effective_acrs"), enabledSet, enabledSet.size());

            //Put it on this bean...
            Stream<CredentialType> stream = enabledSet.stream().map(CredentialType::get);
            enabledMethods = stream.collect(Collectors.toCollection(HashSet::new));
        }
        else
            throw new Exception(Labels.getLabel("app.missing_acr_value", new String[]{acrList.toString()}));

    }

    private boolean computeOxdSettings(Configs settings) throws Exception{

        boolean update=false;

        OxdConfig oxdConfig = settings.getOxdConfig();
        if (oxdConfig==null)
            logger.error(Labels.getLabel("app.no_oxd_config"));
        else{
            Optional<String> oxdHostOpt=Utils.stringOptional(oxdConfig.getHost());
            Optional<String> oxdRedirectUri=Utils.stringOptional(oxdConfig.getRedirectUri());

            if (!(oxdConfig.getPort()>0 && oxdHostOpt.isPresent() && oxdRedirectUri.isPresent()))
                logger.error(Labels.getLabel("app.oxd_settings_missing"));
            else{
                String tmp=oxdRedirectUri.get();    //Remove trailing slash if any in redirect URI
                tmp=tmp.endsWith("/") ? tmp.substring(0, tmp.length()-1) : tmp;
                oxdConfig.setPostLogoutUri(tmp + "/" + WebUtils.LOGOUT_PAGE_URL);

                Optional<String> oxdIdOpt=Utils.stringOptional(oxdConfig.getOxdId());
                if (oxdIdOpt.isPresent()) {
                    oxdService.setSettings(oxdConfig);
                    inOperableState = true;
                }
                else{
                    try{
                        //TODO: delete previous existing client?
                        //Do registration
                        oxdConfig.setClientName("cred-manager");
                        oxdConfig.setOxdId(oxdService.doRegister(oxdConfig));
                        oxdService.setSettings(oxdConfig);

                        update=true;
                        inOperableState = true;
                    }
                    catch (Exception e) {
                        logger.fatal(Labels.getLabel("app.oxd_registration_failed"), e.getMessage());
                        throw e;
                    }
                }
            }
        }
        return update;
    }

    private String guessGluuVersion(){

        String version=null;
        //This is a try-with-resources Statement (JarFile will be automatically closed)
        try
                (JarFile war=new JarFile(new File(OXAUTH_WAR_LOCATION), false, ZipFile.OPEN_READ))
        {
            version=war.getManifest().getMainAttributes().getValue("Implementation-Version");
            if (version!=null) {
                version=version.toLowerCase().replaceFirst("-snapshot", "");
                logger.info(Labels.getLabel("app.gluu_version_guessed"), version);
            }
        }
        catch (Exception e){
            logger.warn(Labels.getLabel("app.gluu_version_not_guessable"), e.getMessage());
        }
        return version;

    }

    public void setLoggingLevel(String strLevel){

        Level newLevel=Level.toLevel(strLevel);
        /*
        LoggerContext loggerContext = LoggerContext.getContext(false);
        for (org.apache.logging.log4j.core.Logger logger : loggerContext.getLoggers()) {
            if (logger.getName().startsWith("org.gluu"))
                logger.setLevel(newLevel);
        }*/
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.gluu", newLevel);
        configSettings.setLogLevel(strLevel);
    }

    private Level getLoggingLevel(){

        //Level currLevel=null;
        LoggerContext loggerContext = LoggerContext.getContext(false);
        return loggerContext.getConfiguration().getLoggerConfig("org.gluu").getLevel();
        /*
        for (org.apache.logging.log4j.core.Logger logger : loggerContext.getLoggers())
            if (logger.getName().startsWith("org.gluu")) {
                currLevel = logger.getLevel();
                break;
            }
        return currLevel; */
    }

}