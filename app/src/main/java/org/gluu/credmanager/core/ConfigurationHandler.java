/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gluu.credmanager.conf.MainSettings;
import org.gluu.credmanager.conf.U2fSettings;
import org.gluu.credmanager.event.AppStateChangeEvent;
import org.gluu.credmanager.extension.AuthnMethod;
import org.gluu.credmanager.misc.AppStateEnum;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.plugins.authnmethod.SecurityKeyExtension;
import org.gluu.credmanager.service.LdapService;
import org.greenrobot.eventbus.EventBus;
import org.quartz.JobExecutionContext;
import org.quartz.listeners.JobListenerSupport;
import org.slf4j.Logger;
import org.zkoss.util.Pair;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jgomer
 */
@Named
@ApplicationScoped
public class ConfigurationHandler extends JobListenerSupport {

    public static final Pair<Integer, Integer> BOUNDS_MINCREDS_2FA = new Pair<>(1, 3);

    private static final int RETRY_INTERVAL = 10; //1
    private static final String DEFAULT_ACR = "credmanager";
    public static final List<String> DEFAULT_SUPPORTED_METHODS = Arrays.asList("u2f", "otp", "super_gluu", "twilio_sms");

    @Inject
    private Logger logger;

    @Inject
    private MainSettings settings;

    @Inject
    private LdapService ldapService;

    @Inject
    private EventBus eventBus;

    @Inject
    private ExtensionsManager extManager;

    @Inject
    private TimerService timerService;

    @Inject
    private LogService logService;

    private Set<String> serverAcrs;

    private String acrQuartzJobName;

    private ObjectMapper mapper;

    private AppStateEnum appState;

    private String issuerUrl;

    @PostConstruct
    private void inited() {
        logger.info("ConfigurationHandler inited");
        mapper = new ObjectMapper();
        acrQuartzJobName = getClass().getSimpleName() + "_acr";
    }

    void init() {

        try {
            //Check LDAP access to proceed with acr timer
            if (ldapService.isInService()) {
                setAppState(AppStateEnum.LOADING);
                issuerUrl = ldapService.getIssuerUrl();

                //This is a trick so the timer event logic can be coded inside this managed bean
                timerService.addListener(this, acrQuartzJobName);
                /*
                 A gap of 5 seconds is enough for the RestEasy scanning process to take place (in case oxAuth is already up and running)
                 A value of 30 gives room of 5 min (300 seconds) to recover the acr list. This big amount of time may be required
                 in cases where cred-manager service starts too soon (even before oxAuth itself)
                */
                timerService.schedule(acrQuartzJobName, 5, 30, RETRY_INTERVAL);
            } else {
                setAppState(AppStateEnum.FAIL);
            }
        } catch (Exception e) {
            setAppState(AppStateEnum.FAIL);
            logger.error(e.getMessage(), e);
        }

    }

    public Integer getMinCredsFor2FA() {
        return settings.getMinCredsFor2FA();
    }

    /**
     * Performs a GET to the OIDC metadata URL and extracts the ACR values supported by the server
     * @return A Set of String values
     * @throws Exception If an networking or parsing error occurs
     */
    public Set<String> retrieveAcrs() {

        try {
            String oidcEndpointURL = ldapService.getOIDCEndpoint();
            logger.debug("Obtaining \"acr_values_supported\" from server {}", oidcEndpointURL);
            JsonNode values = mapper.readTree(new URL(oidcEndpointURL)).get("acr_values_supported");

            //Store server's supported acr values in a set
            serverAcrs = new HashSet<>();
            values.forEach(node -> serverAcrs.add(node.asText()));
        } catch (Exception e) {
            logger.error("Could not retrieve the list of acrs supported by this server: {}", e.getMessage());
            logger.warn("Retrying in {} seconds", RETRY_INTERVAL);
        }
        return serverAcrs;

    }

    @Override
    public String getName() {
        return acrQuartzJobName;
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {

        try {
            if (serverAcrs == null) {
                Date nextJobExecutionAt = context.getNextFireTime();
                //Do an attempt to retrieve acrs
                retrieveAcrs();

                if (serverAcrs == null) {
                    if (nextJobExecutionAt == null) {     //Run out of attempts!
                        logger.warn("The list of supported acrs could not be obtained.");
                        setAppState(AppStateEnum.FAIL);
                    }
                } else {
                    if (serverAcrs.contains(DEFAULT_ACR)) {
                        //Update log level
                        computeLoggingLevel();
                        computeBrandingPath();
                        computeMinCredsForStrongAuth();
                        computePassResetable();
                        if (serverAcrs.contains(SecurityKeyExtension.ACR)) {
                            computeU2fSettings();
                        }

                        extManager.scan();
                        //Ensure there is an openidflow extension
                        //TODO: remove dummy predicate
                        if (false && extManager.getExtensionForOpenIdFlow() == null) {
                            logger.warn("There is no extension registered for OpenId Flow.");
                            setAppState(AppStateEnum.FAIL);
                        } else {
                            refreshAcrPluginMapping();
                            setAppState(AppStateEnum.OPERATING);
                        }
                    } else {
                        logger.error("Your Gluu server is missing one critical acr value: {}.", DEFAULT_ACR);
                        setAppState(AppStateEnum.FAIL);
                    }
                }
                switch (appState) {
                    case FAIL:
                        logger.error("Application not in operable state, please fix configuration issues before proceeding.");
                        logger.info("=== WEBAPP INITIALIZATION FAILED ===");
                        break;
                    case OPERATING:
                        logger.info("=== WEBAPP INITIALIZED SUCCESSFULLY ===");
                        break;
                }
            }
        } catch (Exception e) {
            if (!appState.equals(AppStateEnum.OPERATING)) {
                logger.error(e.getMessage(), e);
            }
        }

    }

    public String getExtraCssSnippet() {
        return settings.getExtraCssSnippet();
    }

    public AppStateEnum getAppState() {
        return appState;
    }

    public boolean isPasswordResetable() {
        return settings.isEnablePassReset();
    }

    public Map<String, Integer> getAcrLevelMapping() {

        Map<String, Integer> map = new HashMap<>();
        try {
            String oidcEndpointURL = ldapService.getOIDCEndpoint();
            JsonNode levels = mapper.readTree(new URL(oidcEndpointURL)).get("auth_level_mapping");
            Iterator<Map.Entry<String, JsonNode>> it = levels.fields();

            while (it.hasNext()) {
                Map.Entry<String, JsonNode> entry = it.next();
                try {
                    Integer levl = Integer.parseInt(entry.getKey());
                    Iterator<JsonNode> arrayIt = entry.getValue().elements();
                    while (arrayIt.hasNext()) {
                        map.put(arrayIt.next().asText(), levl);
                    }
                } catch (Exception e) {
                    logger.error("Error parsing level for {}: {}", entry.getKey(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }
        return map;

    }

    public Set<String> getEnabledAcrs() {
        Set<String> plugged = new HashSet<>(settings.getAcrPluginMap().keySet());
        plugged.retainAll(retrieveAcrs());
        return plugged;
    }

    private void setAppState(AppStateEnum state) {

        if (!state.equals(appState)) {
            eventBus.post(new AppStateChangeEvent(state));
        }
        appState = state;

    }

    private void computeLoggingLevel() {
        settings.setLogLevel(logService.updateLoggingLevel(settings.getLogLevel()));
    }

    private void computeMinCredsForStrongAuth() {

        int defaultValue = (BOUNDS_MINCREDS_2FA.getX() + BOUNDS_MINCREDS_2FA.getY()) / 2;
        Integer providedValue = settings.getMinCredsFor2FA();

        if (providedValue == null) {
            logger.info("Using default value {} for minimum number of credentials to enable strong authentication");
            settings.setMinCredsFor2FA(defaultValue);
        } else {
            if (providedValue < BOUNDS_MINCREDS_2FA.getX() || providedValue > BOUNDS_MINCREDS_2FA.getY()) {
                logger.info("Value for min_creds_2FA={} not in interval [{},{}]. Defaulting to {}", providedValue,
                        BOUNDS_MINCREDS_2FA.getX(), BOUNDS_MINCREDS_2FA.getY(), defaultValue);
                settings.setMinCredsFor2FA(defaultValue);
            }
        }

    }

    private void computeBrandingPath() {

        String path = settings.getBrandingPath();
        try {
            if (Utils.isNotEmpty(path) && !Files.isDirectory(Paths.get(path))) {
                throw new IOException("Not a directory");
            }
        } catch (Exception e) {
            logger.error("Filesystem directory {} for custom branding is wrong. Using default theme", path);
            logger.error(e.getMessage(), e);
            settings.setBrandingPath(null);
        }

    }

    private void computePassResetable() {

        if (settings.isEnablePassReset() && ldapService.isBackendLdapEnabled()) {
            logger.error("Pass reset set automatically to false. Check if you are using a backend LDAP");
            settings.setEnablePassReset(false);
        }

    }

    private void computeU2fSettings() {

        U2fSettings u2fCfg = settings.getU2fSettings();
        boolean nocfg = u2fCfg == null;
        boolean guessAppId = nocfg || u2fCfg.getAppId() == null;
        boolean guessUri = nocfg || u2fCfg.getRelativeMetadataUri() == null;

        if (nocfg) {
            u2fCfg = new U2fSettings();
        }

        if (guessAppId) {
            u2fCfg.setAppId(issuerUrl);
            logger.warn("Metada for {} not found. Value inferred={}", "U2F app ID", issuerUrl);
        }

        String endpointUrl = u2fCfg.getRelativeMetadataUri();
        if (guessUri) {
            endpointUrl = ".well-known/fido-u2f-configuration";

            u2fCfg.setRelativeMetadataUri(endpointUrl);
            logger.warn("Metada for {} not found. Value inferred={}", "U2F relative endpoint URL", endpointUrl);
        }
        u2fCfg.setEndpointUrl(String.format("%s/%s", issuerUrl, endpointUrl));

        try {
            settings.setU2fSettings(u2fCfg);
            logger.info("U2f settings found were: {}", mapper.writeValueAsString(u2fCfg));
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void refreshAcrPluginMapping() {

        Map<String, String> mapping = settings.getAcrPluginMap();

        if (Utils.isEmpty(mapping)) {
            Set<String> acrs = extManager.getAuthnMethodExts().stream().map(AuthnMethod::getAcr).collect(Collectors.toSet());
            acrs.addAll(DEFAULT_SUPPORTED_METHODS);

            //Try to build the map by inspecting system extensions
            mapping = new HashMap<>();
            for (String acr : acrs) {
                if (extManager.pluginImplementsAuthnMethod(acr, null)) {
                    mapping.put(acr, null);
                }
            }
            settings.setAcrPluginMap(mapping);
        } else {
            Map<String, String> newMap = new HashMap<>();
            for (String acr : mapping.keySet()) {
                //Is there a current runtime impl for this acr?
                String plugId = mapping.get(acr);
                if (extManager.pluginImplementsAuthnMethod(acr, plugId)) {
                    newMap.put(acr, plugId);
                } else {
                    if (Utils.isEmpty(plugId)) {
                        logger.warn("There is no system extension that can work with acr '{}'", acr);
                    } else {
                        logger.warn("Plugin {} does not have extensions that can work with acr '{}' or plugin does not exist", plugId, acr);
                    }
                    logger.warn("acr removed from configuration file...");
                }
            }
            settings.setAcrPluginMap(newMap);
            try {
                settings.save();
            } catch (Exception e) {
                logger.error(e.getMessage(), e);
            }
        }

    }

}
