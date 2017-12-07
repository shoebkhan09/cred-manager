package org.gluu.credmanager.core.navigation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.User;
import org.gluu.credmanager.core.WebUtils;
import static org.gluu.credmanager.core.WebUtils.RedirectStage;
import org.gluu.credmanager.services.ServiceMashup;
import org.gluu.credmanager.services.UserService;
import org.gluu.credmanager.services.OxdService;
import org.zkoss.util.Pair;
import org.zkoss.util.resource.Labels;
import org.zkoss.zk.ui.*;
import org.zkoss.zk.ui.util.Initiator;

import javax.management.AttributeNotFoundException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Created by jgomer on 2017-07-16.
 * This is a ZK Page Initiator (the doInit method is called before any rendering of UI components). It's the initiator
 * associated to the /index.zul URL (home URL) so here's where the authentication flow is handled.
 */
public class HomeInitiator extends CommonInitiator implements Initiator {

    private ServiceMashup services;
    private Session se;
    private OxdService oxdService;

    private Logger logger = LogManager.getLogger(getClass());

    //Redirects to an authorization URL obtained with OXD
    private void goForAuthorization() throws Exception{
        WebUtils.setRedirectStage(se, RedirectStage.INITIAL);
        //do Authz Redirect
        //WebUtils.execRedirect(oxdService.getAuthzUrl(services.getAppConfig().getDefaultAcr()));
        WebUtils.execRedirect(oxdService.getAuthzUrl(services.getAppConfig().getDefaultAcr()));
    }

    private User getUserFromClaims(Map<String, List<String>> claims, UserService usrService) throws AttributeNotFoundException{

        User user=usrService.createUserFromClaims(claims);

        if (user==null)
            throw new AttributeNotFoundException(Labels.getLabel("app.user_no_claims"));

        //Update current user with credentials he has added so far:
        user.setCredentials(usrService.getPersonalCredentials(user));
        //Update method
        user.setPreference(usrService.getPreferredMethod(user));
        //Determine if belongs to manager group
        user.setAdmin(usrService.inManagerGroup(user));

        return user;

    }

    public void doInit(Page page, Map <String, Object> map){

        init(page);
        if (page.getAttribute("error")==null){

            se=Sessions.getCurrent(true);
            RedirectStage stage=WebUtils.getRedirectStage(se);
            services=WebUtils.getServices(se);
            oxdService=services.getOxdService();

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
                        //If IDP response contains error query parameter we cannot proceed
                        if (errorsParsed(page))
                            WebUtils.purgeSession(se);
                        else {
                            String code = WebUtils.getQueryParam("code");
                            if (code == null)
                                //This may happen when user did not ever entered his username at IDP, and tries accessing the app again
                                goForAuthorization();
                            else {
                                Pair<String, String> tokens=oxdService.getTokens(code, WebUtils.getQueryParam("state"));
                                String accessToken = tokens.getX();
                                String idToken=tokens.getY();
                                logger.debug(Labels.getLabel("app.authz_codes"), code, accessToken, idToken);

                                User user = getUserFromClaims(oxdService.getUserClaims(accessToken), services.getUserService());
                                //Store in session
                                WebUtils.setUser(se, user);
                                WebUtils.setIdToken(se, idToken);

                                WebUtils.setRedirectStage(se, RedirectStage.BYPASS);
                                //This flow continues at index.zul
                            }
                        }
                        break;
                    case BYPASS:
                        //go straight without the need for showing UI
                        User user=WebUtils.getUser(se);
                        WebUtils.execRedirect(user.isAdmin()? WebUtils.ADMIN_PAGE_URL : WebUtils.USER_PAGE_URL);
                        break;
                }
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
                setPageErrors(page, Labels.getLabel("general.error.general"), e.getMessage());
                WebUtils.setRedirectStage(se, RedirectStage.NONE);
            }
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