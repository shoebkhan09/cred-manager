package org.gluu.credmanager.conf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.Configs;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.gluu.credmanager.conf.jsonized.U2fSettings;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.OxdService;
import org.zkoss.util.resource.Labels;

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
 */
@ApplicationScoped
public class AppConfiguration{

    private final String DEFAULT_GLUU_BASE="/etc/gluu";
    private final String CONF_FILE_RELATIVE_PATH="conf/cred-manager.json";
    private final String OXAUTH_WAR_LOCATION= "/opt/gluu/jetty/oxauth/webapps/oxauth.war";
    private final String DEFAULT_GLUU_VERSION="3.0.1";
    public static final int ACTIVATE2AF_CREDS_GTE=2;

    //========== Properties exposed by this service ==========

    private Configs configSettings;
    private boolean inOperableState=false;  //Use pesimistic approach (assume it's likelier to fail than to succeed)
    private String orgName;
    private String issuerUrl;

    //The following override those properties inside found inside configSettings
    private Set<CredentialType> enabledMethods;
    private boolean passReseteable;
    private String gluuVersion;
    //============================================================

    private Logger logger = LogManager.getLogger(getClass());
    private ObjectMapper mapper=new ObjectMapper();

    private String gluuBase;

    @Inject
    private LdapService ldapService;

    @Inject
    private OxdService oxdService;

    public String getOrgName() {
        return orgName;
    }

    public boolean isPassReseteable() {
        return passReseteable;
    }

    public String getGluuVersion() {
        return gluuVersion;
    }

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

    private void setGluuBase(String candidateGluuBase) {

        String osName = System.getProperty("os.name").toLowerCase();
        boolean windows = osName.matches(".*win.*");

        if (candidateGluuBase==null && !windows)
            gluuBase = DEFAULT_GLUU_BASE;
        else
            gluuBase = candidateGluuBase;

    }

    private File getConfigFile(String baseDir){
        Path path=Paths.get(baseDir, CONF_FILE_RELATIVE_PATH);
        return Files.exists(path) ? path.toFile() : null;
    }

    public AppConfiguration() {}

    @PostConstruct
    public void setup(){
        setGluuBase(System.getProperty("gluu.base"));

        if (gluuBase!=null){
            File src=getConfigFile(gluuBase);
            if (src==null)
                logger.error(Labels.getLabel("app.conf_file_not_readable"), CONF_FILE_RELATIVE_PATH);
            else
                try{
                    configSettings=mapper.readValue(src, Configs.class);
                }
                catch (IOException e){
                    inOperableState=false;
                    String params[]=new String[]{CONF_FILE_RELATIVE_PATH, e.getMessage()};
                    logger.error(Labels.getLabel("app.conf_file_not_parsable"), params);
                    logger.error(e.getMessage(),e);
                }
                finally {
                    if (computeSettings(configSettings))
                        try {
                            //update file to disk
                            mapper.enable(SerializationFeature.INDENT_OUTPUT);
                            mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
                            mapper.writeValue(src, configSettings);
                        }
                        catch (Exception e) {
                            logger.error(Labels.getLabel("app.conf_update_error"), e);
                        }
                }
        }

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
                        computeGluuVersion(settings);
                        computePassReseteable(settings, ldapService.isBackendLdapEnabled());
                        computeEnabledMethods(settings);
                        computeU2fSettings(settings); //Call this after computeEnabledMethods and computeGluuVersion only
                        computeOTPSettings(settings);//Call this after computeEnabledMethods only
                        computeTwilioSettings(settings);//Call this after computeEnabledMethods only

                        OxdConfig oxdConfig = settings.getOxdConfig();
                        if (oxdConfig==null)
                            logger.error(Labels.getLabel("app.no_oxd_config"));
                        else{
                            Optional<String> oxdHostOpt=Utils.stringOptional(oxdConfig.getHost());
                            Optional<String> oxdRedirectUri=Utils.stringOptional(oxdConfig.getRedirectUri());
                            Optional<String> oxdLogoutUri=Utils.stringOptional(oxdConfig.getPostLogoutUri());

                            if (!(oxdConfig.getPort()>0 && oxdHostOpt.isPresent() && oxdLogoutUri.isPresent() && (oxdRedirectUri.isPresent())))
                                logger.error(Labels.getLabel("app.oxd_settings_missing"));
                            else{
                                Optional<String> oxdIdOpt=Utils.stringOptional(oxdConfig.getOxdId());
                                if (oxdIdOpt.isPresent()) {
                                    oxdService.setSettings(oxdConfig);
                                    inOperableState = true;
                                }
                                else{
                                    try{
                                        //Do registration
                                        oxdConfig.setClientName("cred-manager");
                                        oxdConfig.setOxdId(oxdService.doRegister(oxdConfig));
                                        oxdService.setSettings(oxdConfig);

                                        update=true;
                                        inOperableState = true;
                                    }
                                    catch (Exception e) {
                                        logger.error(Labels.getLabel("app.oxd_registration_failed"), e.getMessage());
                                        throw e;
                                    }
                                }
                            }
                        }
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

    private void computePassReseteable(Configs settings, boolean withBackend){

        passReseteable=settings.isEnablePassReset();
        if (settings.isEnablePassReset() && withBackend) {
            logger.error(Labels.getLabel("app.pass_reset_turnedoff"));
            passReseteable=false;
        }

    }

    private void computeOTPSettings(Configs settings) throws Exception{
        if (enabledMethods.contains(CredentialType.OTP))
            settings.setOtpConfig(OTPConfig.get(ldapService.getOTPScriptInfo()));
    }

    private void computeTwilioSettings(Configs settings) throws Exception{
        if (enabledMethods.contains(CredentialType.VERIFIED_PHONE))
            settings.setTwilioConfig(TwilioConfig.get(ldapService.getSmsScriptInfo()));
    }

    private void computeU2fSettings(Configs settings) throws Exception{

        if (enabledMethods.contains(CredentialType.SECURITY_KEY)) {

            U2fSettings u2fCfg = settings.getU2fSettings();
            boolean nocfg= u2fCfg == null;
            boolean guessAppId = nocfg || u2fCfg.getAppId() == null;
            boolean guessUri = nocfg || u2fCfg.getRelativeMetadataUri() == null;

            if (nocfg)
                u2fCfg = new U2fSettings();

            if (guessAppId) {
                u2fCfg.setAppId(issuerUrl);
                logger.warn(Labels.getLabel("app.metadata_guessed"), "U2F app ID", issuerUrl);
            }

            String endpointUrl=u2fCfg.getRelativeMetadataUri();
            if (guessUri) {

                switch (gluuVersion) {
                    case "3.0.1":
                    case "3.0.2":
                        endpointUrl = ".well-known/fido-u2f-configuration";
                        break;
                    default:
                        endpointUrl = "restv1/fido-u2f-configuration";
                }

                u2fCfg.setRelativeMetadataUri(endpointUrl);
                logger.warn(Labels.getLabel("app.metadata_guessed"), "U2F relative endpoint URL", endpointUrl);
            }
            u2fCfg.setEndpointUrl(String.format("%s/%s", issuerUrl, endpointUrl));

            settings.setU2fSettings(u2fCfg);
            logger.info(Labels.getLabel("app.u2f_settings"), mapper.writeValueAsString(u2fCfg));
        }
    }

    private void computeEnabledMethods(Configs settings) throws Exception{

        String strArr[]=new String[]{};
        String OIDCEndpointURL=ldapService.getOIDCEndpoint();
        Set<String> possibleMethods=new HashSet(CredentialType.ACR_NAMES_SUPPORTED);

        logger.debug(Labels.getLabel("app.obtaining_acrs"), OIDCEndpointURL);
        JsonNode values=mapper.readTree(new URL(OIDCEndpointURL)).get("acr_values_supported");

        //Store server's supported acr values in a set
        Set<String> supportedSet=new HashSet<>();
        values.forEach(node -> supportedSet.add(node.asText()));
        //Add them all to oxd configuration object. These will be a superset of methods used in practice...
        settings.getOxdConfig().setAcrValues(new HashSet(supportedSet));
        //Now, keep the interesting ones. This will filter things like "basic", "auth_ldap_server", etc.
        supportedSet.retainAll(possibleMethods);

        Optional<String[]> methods=Utils.arrayOptional(settings.getEnabledMethods());
        //If there are no enabled methods in config file, assume we want all of them
        String tmp[]=(methods.isPresent()) ? methods.get() : possibleMethods.toArray(strArr);

        //From enabled methods in config file, keep only those really supported by server
        Set<String> enabledSet=new HashSet<>(Arrays.asList(tmp));
        enabledSet.retainAll(supportedSet);

        //log result so far
        strArr=enabledSet.toArray(strArr);
        logger.warn(Labels.getLabel("app.effective_acrs"), Arrays.asList(strArr).toString(), strArr.length);

        //Put it on this bean...
        Stream<CredentialType> stream=enabledSet.stream().map(CredentialType::get);
        enabledMethods=stream.collect(Collectors.toCollection(HashSet::new));

    }

    private String guessGluuVersion(){

        String version=null;
        //This is a try-with-resources Statement (JarFile will be automatically closed)
        try
                (JarFile war=new JarFile(new File(OXAUTH_WAR_LOCATION), false, ZipFile.OPEN_READ))
        {
            version=war.getManifest().getMainAttributes().getValue("Implementation-Version");
            if (version!=null)
                version.toLowerCase().replaceFirst("-snapshot","");
        }
        catch (Exception e){
            logger.warn(Labels.getLabel("app.gluu_version_not_guessable"), e.getMessage());
        }
        return version;

    }

}