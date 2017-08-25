package org.gluu.credmanager.core.init;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.zkoss.util.resource.Labels;

import javax.inject.Inject;
import javax.servlet.ServletContextAttributeEvent;
import javax.servlet.ServletContextAttributeListener;
import javax.servlet.annotation.WebListener;

/**
 * Created by jgomer on 2017-08-19.
 */
@WebListener
public class ContextAttributeListener implements ServletContextAttributeListener {

    private Logger logger = LogManager.getLogger(getClass());

    @Inject
    private AppConfiguration appConfiguration;

    public void attributeAdded(ServletContextAttributeEvent event){

        switch (event.getName()){
            case ZKInitializer.ZK_READY_ATTR:
                if (new Boolean(event.getValue().toString()))
                    proceed();
                break;
        }
    }

    public void attributeRemoved(ServletContextAttributeEvent event){

    }

    public void attributeReplaced(ServletContextAttributeEvent event){

    }

    public void proceed(){
        if (appConfiguration.isInOperableState())
            logger.info(Labels.getLabel("app.webapp_init_ok"));
        else {
            logger.info(Labels.getLabel("app.webapp_init_failure"));
            logger.error(Labels.getLabel("app.notoperable"));
        }
    }

}