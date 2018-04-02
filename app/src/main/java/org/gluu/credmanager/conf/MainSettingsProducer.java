/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.conf;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.inject.Inject;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jgomer
 */
@ApplicationScoped
public class MainSettingsProducer {

    private static final String DEFAULT_GLUU_BASE = "/etc/gluu";
    private static final String CONF_FILE_RELATIVE_PATH = "conf/cred-manager2.json";
    private static final String DEFAULT_EXTERNAL_PATH = "/opt/gluu/jetty/cred-manager/custom";

    @Inject
    private Logger logger;

    private String getGluuBase() {
        String candidateGluuBase = System.getProperty("gluu.base");
        boolean windows = System.getProperty("os.name").toLowerCase().matches(".*win.*");

        return (candidateGluuBase != null || windows) ? candidateGluuBase : DEFAULT_GLUU_BASE;

    }

    /**
     * Returns a reference to the configuration file of the application (cred-manager.json)
     * @param baseDir Path to configuration file without the CONF_FILE_RELATIVE_PATH part
     * @return A File object
     */
    private File getConfigFile(String baseDir) {
        Path path = Paths.get(baseDir, CONF_FILE_RELATIVE_PATH);
        return Files.exists(path) ? path.toFile() : null;
    }

    private String getActualExternalPath(String path) {

        String realPath = null;
        if (Utils.isNotEmpty(path) && Files.isDirectory(Paths.get(path))) {
            realPath = path;
        }

        if (realPath == null) {
            logger.warn("External assets path {} does not exist", path);

            if (Files.isDirectory(Paths.get(DEFAULT_EXTERNAL_PATH))) {
                realPath = DEFAULT_EXTERNAL_PATH;
                logger.info("Defaulting external assets path to {}", realPath);
            }
        }
        if (realPath == null) {
            logger.warn("External assets path could not be determined");
        }

        return realPath;

    }

    @Produces @ApplicationScoped
    public MainSettings instance() {

        MainSettings settings = null;
        logger.info("init. Obtaining global settings");

        String gluuBase = getGluuBase();
        logger.info("init. Gluu base inferred was {}", gluuBase);

        if (gluuBase != null) {
            //Get a reference to the config-file
            File srcConfigFile = getConfigFile(gluuBase);

            if (srcConfigFile == null) {
                logger.error("init. Cannot read configuration file {}", CONF_FILE_RELATIVE_PATH);
            } else {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
                    //Parses config file in a Configs instance
                    settings = mapper.readValue(srcConfigFile, MainSettings.class);
                    settings.setSourceFile(srcConfigFile);

                    settings.setPluginsPath(getActualExternalPath(settings.getPluginsPath()));

                    List<String> enabledMethods = settings.getEnabledMethods();
                    Map<String, String> acrPluginMapping = settings.getAcrPluginMap();
                    if (Utils.isNotEmpty(enabledMethods)) {

                        if (Utils.isEmpty(acrPluginMapping)) {
                            //If acr plugin mapping does not exist and deprecated "enabled_methods" property does, migrate data
                            acrPluginMapping = new HashMap<>();
                            for (String acr : enabledMethods) {
                                acrPluginMapping.put(acr, null);
                            }
                            settings.setAcrPluginMap(acrPluginMapping);
                        }
                        //Dismiss "enabled_methods" contents
                        settings.setEnabledMethods(null);
                        settings.save();
                    }
                } catch (Exception e) {
                    logger.error("Error parsing configuration file {}", CONF_FILE_RELATIVE_PATH);
                    logger.error(e.getMessage(), e);
                }
            }
        }
        return settings;

    }

}
