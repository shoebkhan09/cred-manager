package org.gluu.credmanager.core.init;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.services.ServiceMashup;
import javax.inject.Inject;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

import static org.gluu.credmanager.core.WebUtils.SERVICES_ATTRIBUTE;

/**
 * Created by jgomer on 2017-07-08.
 */
public class SessionListener implements HttpSessionListener {

    @Inject
    private ServiceMashup services;

    private Logger logger = LogManager.getLogger(getClass());

    public void sessionCreated(HttpSessionEvent hse){
        HttpSession session= hse.getSession();
        logger.info("Session created {} ", hse.getSession().getId());
        session.setAttribute(SERVICES_ATTRIBUTE, services);
    }

    public void sessionDestroyed(HttpSessionEvent hse){
        logger.info("Session destroyed {}", hse.getSession().getId());
    }

}