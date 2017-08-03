package org.gluu.credmanager.core.navigation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.CredentialType;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import static org.gluu.credmanager.core.WebUtils.RedirectStage;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
import org.gluu.credmanager.services.OxdService;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.*;
import org.zkoss.zk.ui.util.Initiator;

import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-07-16.
 */
public class HomeInitiator extends FailedPage implements Initiator {

    private ServiceMashup services;
    private String code;

    private Logger logger = LogManager.getLogger(getClass());

    public void doInit(Page page, Map <String, Object> map){

        //logger.info(Labels.getLabel("app.landed_home_from"), WebUtils.getRequestHeader("Referer"));
        Session se=Sessions.getCurrent(true);
        RedirectStage stage=WebUtils.getRedirectStage(se);

        services=WebUtils.getServices(se);
        OxdService oxdService=services.getOxdService();
        UserService usrService=services.getUserService();

        try {
            switch (stage){
                case NONE:
                    WebUtils.setRedirectStage(se, RedirectStage.INITIAL);
                    //do Initial Authz Redirect
                    WebUtils.execRedirect(oxdService.getDefaultAuthzUrl());
                    break;
                case INITIAL:
                    if (!errorProcessed(page)){
                        code = WebUtils.getQueryParam("code");
                        String accessToken=oxdService.getAccessToken(code, WebUtils.getQueryParam("state"));
                        logger.debug(Labels.getLabel("app.authz_codes"), code, accessToken);

                        Map<String, List<String>> claims=oxdService.getUserClaims(accessToken);
                        User user=usrService.createUserFromClaims(claims);
                        WebUtils.setUser(se, user);

                        CredentialType credType=usrService.getPreferredMethod(user);
                        if (credType==null) {     //Preferred method not set, go straight to landing page
                            WebUtils.setRedirectStage(se, RedirectStage.BYPASS);
                            WebUtils.execRedirect(user.isAdmin() ? WebUtils.ADMIN_PAGE_URL : WebUtils.USER_PAGE_URL);
                        }
                        else    //See the accompanying view model of this zul page
                            WebUtils.setRedirectStage(se, RedirectStage.REAUTHENTICATE);
                    }
                    break;
                case FINAL:
                    /*
                    Assume not getting an error query param and simply getting an authz code is enough to let user pass.
                    All claims were already gathered (INITIAL phase), so user object is in session now
                    */
                    if (!errorProcessed(page) && WebUtils.getQueryParam("code")!=null){
                        User user=WebUtils.getUser(se);
                        WebUtils.setRedirectStage(se, RedirectStage.BYPASS);
                        WebUtils.execRedirect(user.isAdmin()? WebUtils.ADMIN_PAGE_URL : WebUtils.USER_PAGE_URL);
                    }
                    break;
                case BYPASS:
                    //Nothing much to check
                    User user=WebUtils.getUser(se);
                    WebUtils.execRedirect(user.isAdmin()? WebUtils.ADMIN_PAGE_URL : WebUtils.USER_PAGE_URL);
                    break;
            }
        }
        catch (Exception e){
            logger.error(e.getMessage(), e);
            setPageErrors(page, Labels.getLabel("general.error.general"), e.getMessage());
        }

    }

    private boolean errorProcessed(Page page){

        String error=WebUtils.getQueryParam("error");
        boolean errorsFound=error!=null;
        if (errorsFound)
            setPageErrors(page, error, WebUtils.getQueryParam("error_description"));
        return errorsFound;

    }

}