/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.gluu.credmanager.misc.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Inject;
import javax.inject.Named;

/**
 * Most methods in this class assumes the SLF4J binding employed is Log4j2. If binding is changed, methods need to be
 * updated.
 * @author jgomer
 */
@Named
@ApplicationScoped
public class LogService {

    @Inject
    private Logger logger;

    @Produces
    public Logger loggerInstance(InjectionPoint ip) {
        return LoggerFactory.getLogger(ip.getMember().getDeclaringClass().getName());
    }

    public String updateLoggingLevel(String levelInConfFile) {

        String currentLevl = getLoggingLevel().name();
        String value = currentLevl;

        if (Utils.isEmpty(levelInConfFile)) {
            logger.info("Defaulting to {} for log level", currentLevl);
        } else {
            try {
                Level.valueOf(levelInConfFile);
                setLoggingLevel(levelInConfFile);
                logger.info("Using {} for log level", levelInConfFile);
                value = levelInConfFile;
            } catch (Exception e) {
                logger.warn("Log level {} supplied is not valid. Defaulting to {}", levelInConfFile, currentLevl);
            }
        }
        return value;

    }

    private void setLoggingLevel(String strLevel) {

        Level newLevel = Level.toLevel(strLevel);
        /*
        LoggerContext loggerContext = LoggerContext.getContext(false);
        for (org.apache.logging.log4j.core.Logger logger : loggerContext.getLoggers()) {
            if (logger.getName().startsWith("org.gluu"))
                logger.setLevel(newLevel);
        }*/
        org.apache.logging.log4j.core.config.Configurator.setLevel("org.gluu.credmanager", newLevel);
    }

    private Level getLoggingLevel() {

        //Level currLevel=null;
        LoggerContext loggerContext = LoggerContext.getContext(false);
        return loggerContext.getConfiguration().getLoggerConfig("org.gluu.credmanager").getLevel();
        /*
        for (org.apache.logging.log4j.core.Logger logger : loggerContext.getLoggers())
            if (logger.getName().startsWith("org.gluu")) {
                currLevel = logger.getLevel();
                break;
            }
        return currLevel; */
    }

}
