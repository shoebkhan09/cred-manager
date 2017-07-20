package org.gluu.credmanager.conf;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.jsonized.ConfigFile;
import org.gluu.credmanager.conf.jsonized.OxdConfig;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.LdapService;
import org.gluu.credmanager.services.oxd.OxdService;
import org.zkoss.util.resource.Labels;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
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
public class AppConfiguration {

    private final Charset DEFAULT_CHARSET=Charset.forName("UTF-8"); //Charset for reading .properties
    private final String DEFAULT_GLUU_BASE="/etc/gluu";
    private final String CONF_FILE_RELATIVE_PATH="conf/cred-manager.json";
    private final String OXAUTH_WAR_LOCATION= "/opt/gluu/jetty/oxauth/webapps/oxauth.war";
    private final String DEFAULT_GLUU_VERSION="3.0.1";


    //========== Properties exposed by this service ==========

    private ConfigFile configSettings;
    private boolean inOperableState=false;  //Use pesimistic approach (assume it's likelier to fail than to succeed)
    private String orgName;

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

    public ConfigFile getConfigSettings() {
        return configSettings;
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

    public AppConfiguration() {
    }

    public void setup(){

        setGluuBase(System.getProperty("gluu.base"));

        if (gluuBase!=null){
            File src=getConfigFile(gluuBase);
            if (src==null)
                logger.error(Labels.getLabel("app.conf_file_not_readable"), CONF_FILE_RELATIVE_PATH);
            else
                try{
                    configSettings=mapper.readValue(src, ConfigFile.class);
                }
                catch (IOException e){
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
                            catch (Exception e){
                                logger.error(Labels.getLabel("app.conf_update_error"), e);
                            }
                }
        }

    }

    private boolean computeSettings(ConfigFile settings) {

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
                        computePassReseteable(settings, ldapService.isBackendLdapEnabled());
                        computeEnabledMethods(settings);
                        computeGluuVersion(settings);

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

    private void computeGluuVersion(ConfigFile settings){

        Optional<String> optGluu = Utils.stringOptional(settings.getGluuVersion());
        optGluu = Utils.stringOptional(optGluu.orElse(guessGluuVersion()));   //try guessing if necessary
        gluuVersion=optGluu.orElse(DEFAULT_GLUU_VERSION);      //use default if needed

    }

    private void computePassReseteable(ConfigFile settings, boolean withBackend){

        passReseteable=settings.isEnablePassReset();
        if (settings.isEnablePassReset() && withBackend) {
            logger.error(Labels.getLabel("app.pass_reset_turnedoff"));
            passReseteable=false;
        }

    }

    private void computeEnabledMethods(ConfigFile settings) throws Exception{

        String strArr[]=new String[]{};
        String OIDCEndpointURL=ldapService.getOIDCEndpoint();
        Set<String> possibleMethods=new HashSet(CredentialType.ACR_NAMES_SUPPORTED);

        logger.debug(Labels.getLabel("app.obtaining_acrs"), OIDCEndpointURL);
        JsonNode values=mapper.readTree(new URL(OIDCEndpointURL)).get("acr_values_supported");

        //Store server's supported acr values in a set
        Set<String> supportedSet=new HashSet<>();
//supportedSet.addAll(Arrays.asList("twilio","u2f"));
        values.forEach(node -> supportedSet.add(node.asText()));
        //Add them all to oxd configuration object. These will be a superset of methods used in practice...
        settings.getOxdConfig().setAcrValues(new HashSet(supportedSet));
        //Now, keep the interesting ones. This will filter things like "basic", "auth_ldap_server", etc.
        supportedSet.retainAll(possibleMethods);

//logger.debug("retained " + supportedSet.toString());
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
        Stream<CredentialType> stream=enabledSet.stream().map(str -> CredentialType.getType(str));
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