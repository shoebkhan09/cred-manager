package org.gluu.credmanager.core.init;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.zkoss.util.resource.Labels;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

/**
 * Created by jgomer on 2017-07-07.
 */
@ApplicationScoped
public class ContextListener implements ServletContextListener {

    static final String SELF_ATTRIBUTE="CTX_LISTENER";
    private Logger logger = LogManager.getLogger(getClass());

    @Inject
    private AppConfiguration appConfiguration;

    public void contextInitialized(ServletContextEvent sce){
        sce.getServletContext().setAttribute(SELF_ATTRIBUTE, this);
        logger.info("contextInitialized event received...");
    }

    public void contextDestroyed(ServletContextEvent sce){
        logger.info("contextDestroyed event received...");
    }

    public void proceed(){

        appConfiguration.setup();
        if (appConfiguration.isInOperableState())
            logger.info(Labels.getLabel("app.webapp_init_ok"));
        else {
            logger.info(Labels.getLabel("app.webapp_init_failure"));
            logger.error(Labels.getLabel("app.notoperable"));
        }
    }
}
