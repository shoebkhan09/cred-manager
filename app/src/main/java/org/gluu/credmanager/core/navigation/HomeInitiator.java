package org.gluu.credmanager.core.navigation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import static org.gluu.credmanager.core.WebUtils.RedirectStage;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
import org.gluu.credmanager.services.OxdService;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.*;
import org.zkoss.zk.ui.util.Initiator;

import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-07-16.
 */
public class HomeInitiator extends CommonInitiator implements Initiator {

    private ServiceMashup services;
    private String code;
    private Session se;
    private OxdService oxdService;

    private Logger logger = LogManager.getLogger(getClass());

    public void goForAuthorization() throws Exception{
        WebUtils.setRedirectStage(se, RedirectStage.INITIAL);
        //do Authz Redirect
        WebUtils.execRedirect(oxdService.getAuthzUrl(services.getAppConfig().getRoutingAcr()));
    }

    public void doInit(Page page, Map <String, Object> map){

        //logger.info(Labels.getLabel("app.landed_home_from"), WebUtils.getRequestHeader("Referer"));
        init(page);
        se=Sessions.getCurrent(true);
        RedirectStage stage=WebUtils.getRedirectStage(se);

        services=WebUtils.getServices(se);
        oxdService=services.getOxdService();
        UserService usrService=services.getUserService();

        try {
            switch (stage){
                case NONE:
                    try {
                        goForAuthorization();
                    }
                    catch (Exception e){
                        String error=Labels.getLabel("app.error_authorization_step");
                        setPageErrors(page, error, e.getMessage());
                        logger.error(error, e);
                    }
                    break;
                case INITIAL:
                    if (errorsParsed(page))
                        WebUtils.purgeSession(se);
                    else {
                        code = WebUtils.getQueryParam("code");
                        if (code == null)
                            //This may happen when user did not complete the challenging process at IDP, and tries accessing the app
                            goForAuthorization();
                        else {
                            String accessToken = oxdService.getAccessToken(code, WebUtils.getQueryParam("state"));
                            logger.debug(Labels.getLabel("app.authz_codes"), code, accessToken);

                            Map<String, List<String>> claims = oxdService.getUserClaims(accessToken);

                            User user = usrService.createUserFromClaims(claims);
                            //Update current user with credentials he has added so far:
                            user.setCredentials(usrService.getPersonalMethods(user));
                            //Update method
                            user.setPreference(usrService.getPreferredMethod(user));
                            //Store in session
                            WebUtils.setUser(se, user);

                            WebUtils.setRedirectStage(se, RedirectStage.BYPASS);
                            //This flow continues at index.zul
                        }
                    }
                    break;
                case BYPASS:
                    //Check offset is there, otherwise default to UTC
                    if (WebUtils.getUserOffset(se)==null)
                        WebUtils.setUserOffset(se, ZoneOffset.UTC);

                    //go straight without the need for showing UI
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

    private boolean errorsParsed(Page page){

        String error=WebUtils.getQueryParam("error");
        boolean errorsFound=error!=null;
        if (errorsFound)
            setPageErrors(page, error, WebUtils.getQueryParam("error_description"));
        return errorsFound;

    }

}