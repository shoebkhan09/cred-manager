package org.gluu.credmanager.core;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ServiceMashup;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.Execution;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.Session;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Created by jgomer on 2017-07-16.
 */
public class WebUtils {

    public enum RedirectStage {NONE, INITIAL, REAUTHENTICATE, FINAL, BYPASS};
    public static final String USER_PAGE_URL ="user.zul";
    public static final String ADMIN_PAGE_URL ="admin.zul";
    public static final String HOME_PAGE_URL ="index.zul";

    public static final String SERVICES_ATTRIBUTE="SRV";
    public static final String USER_ATTRIBUTE="USR";
    public static final String REDIRECT_STAGE_ATTRIBUTE="REDIR_ST";

    private static Logger logger = LogManager.getLogger(WebUtils.class);

    public static User getUser(Session session){
        return (User)session.getAttribute(USER_ATTRIBUTE);
    }

    public static void setUser(Session session, User user){
        session.setAttribute(USER_ATTRIBUTE, user);
    }

    public static RedirectStage getRedirectStage(Session session){
        RedirectStage stage=(RedirectStage)session.getAttribute(REDIRECT_STAGE_ATTRIBUTE);
        if (stage==null){
            stage=RedirectStage.NONE;
            setRedirectStage(session, stage);
        }
        return stage;
    }

    public static void setRedirectStage(Session session, RedirectStage stage){
        session.setAttribute(REDIRECT_STAGE_ATTRIBUTE, stage);
    }

    public static String getQueryParam(String param){
        Optional<String[]> values=Utils.arrayOptional(Executions.getCurrent().getParameterValues(param));
        return values.isPresent() ? values.get()[0] : null;
    }

    public static void execRedirect(String url){

        try {
            Execution exec = Executions.getCurrent();
            HttpServletResponse response = (HttpServletResponse) exec.getNativeResponse();

            logger.info(Labels.getLabel("app.redirecting_to"),url);
            response.sendRedirect(response.encodeRedirectURL(url));
            exec.setVoided(true); //no need to create UI since redirect will take place
        }
        catch (IOException e){
            logger.error(e.getMessage(),e);
        }

    }

    public static String getRequestHeader(String headerName){
        HttpServletRequest req= (HttpServletRequest)Executions.getCurrent().getNativeRequest();
        return req.getHeader(headerName);
    }

    public static String getCookie(String name){
        HttpServletRequest req= (HttpServletRequest)Executions.getCurrent().getNativeRequest();
        Stream<Cookie> cooks=Arrays.asList(req.getCookies()).stream();
        Optional<Cookie> cookie=cooks.filter(cook -> cook.getName().equals(name)).findFirst();
        return cookie.isPresent()? cookie.get().getValue() : null;
    }

    public static ServiceMashup getServices(Session session){
        return (ServiceMashup) session.getAttribute(SERVICES_ATTRIBUTE);
    }

    public static void purgeSession(Session session){
        session.removeAttribute(USER_ATTRIBUTE);
        session.removeAttribute(REDIRECT_STAGE_ATTRIBUTE);
    }

}