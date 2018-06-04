/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.ui.vm;

import org.gluu.credmanager.core.SessionContext;
import org.gluu.credmanager.core.UserService;
import org.gluu.credmanager.core.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.bind.annotation.*;
import org.zkoss.json.JSONObject;
import org.zkoss.zk.au.out.AuInvoke;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.Executions;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.select.Selectors;
import org.zkoss.zk.ui.select.annotation.Listen;
import org.zkoss.zk.ui.select.annotation.VariableResolver;
import org.zkoss.zk.ui.select.annotation.WireVariable;
import org.zkoss.zk.ui.util.Clients;
import org.zkoss.zkplus.cdi.DelegatingVariableResolver;

import java.time.ZoneOffset;
import java.util.Optional;

/**
 * This class is employed to store in session some user settings
 * @author jgomer
 */
@VariableResolver(DelegatingVariableResolver.class)
public class HomeViewModel {

    private Logger logger = LoggerFactory.getLogger(getClass());

    @WireVariable
    private SessionContext sessionContext;

    @WireVariable
    private UserService userService;

    @AfterCompose
    public void afterCompose(@ContextParam(ContextType.VIEW) Component view) {
        Selectors.wireEventListeners(view, this);
    }

    @Listen("onData=#message")
    public void notified(Event evt) {

        Optional<JSONObject> opt = Optional.ofNullable(evt.getData()).map(JSONObject.class::cast);
        if (opt.isPresent()) {
            JSONObject jsonObject = opt.get();
            logger.info("Browser data is {} ", jsonObject.toJSONString());

            updateOffset(jsonObject.get("offset"));
            updateMobile(jsonObject.get("screenWidth"));
            updateU2fSupport(jsonObject.get("name"), jsonObject.get("version"));
        }
        updateCssPath();
        //reloads this page so the navigation flow proceeds (see HomeInitiator class)
        //TODO: remove
        //Executions.sendRedirect(null);

    }

    @Init
    public void init() {
        Clients.response(new AuInvoke("sendBrowserData"));

        //TODO: remove this
        User user = new User();
        user.setGivenName("admin");
        user.setAdmin(true);
        user.setUserName("admin");
        user.setPreferredMethod("twilio_sms");
        user.setId("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!A8F2.DE1E.D7FB");
        //user.setId("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!XXXX.AAAA.1111");
        sessionContext.setUser(user);

        userService.setupRequisites(user.getId());

/*
        AuthnMethod twilioHandler = extensionsManager.getExtensionForAuthnMethod("twilio_sms");
        twilioHandler.getEnrolledCreds("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!A8F2.DE1E.D7FB");

        AuthnMethod otpHandler = extensionsManager.getExtensionForAuthnMethod("otp");
        otpHandler.getEnrolledCreds("@!3245.DF39.6A34.9E97!0001!513A.9888!0000!A8F2.DE1E.D7FB");
*/
    }

    private void updateOffset(Object value) {

        try {
            if (sessionContext.getZoneOffset() == null) {
                int offset = (int) value;
                ZoneOffset zoffset = ZoneOffset.ofTotalSeconds(offset);
                sessionContext.setZoneOffset(zoffset);
                logger.trace("Time offset for session is {}", zoffset.toString());
                //Ideally zone should be associated to something like "America/Los_Angeles", not a raw offset like "GMT+0010"
                //but this is not achievable unless the user is asked to directly provide his zone
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void updateMobile(Object width) {

        try {
            boolean mobile = Executions.getCurrent().getBrowser("mobile") != null;
            sessionContext.setOnMobileBrowser(mobile);
            logger.trace("Detected browser is {} mobile", mobile ? "" : "not");

            int w = (int) width;
            //This attrib should be in the session, but it's more comfortable at the desktop level for testing purposes
            sessionContext.setOnMobile(mobile && w < 992);    //If screen is wide enough, behave as desktop
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void updateU2fSupport(Object browserName, Object browserVersion) {

        try {
            if (sessionContext.getU2fSupported() == null) {
                //Assume u2f is not supported
                sessionContext.setU2fSupported(false);

                String name = browserName.toString().toLowerCase();
                if (name.contains("chrome")) {
                    sessionContext.setU2fSupported(true);
                } else {
                    if (name.contains("opera")) {
                        String version = browserVersion.toString();
                        int browserVer = version.indexOf(".");

                        if (browserVer > 0) {
                            browserVer = Integer.parseInt(version.substring(0, browserVer));
                            sessionContext.setU2fSupported(browserVer > 40);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
        }

    }

    private void updateCssPath() {

        String path = "org.gluu.credmanager.css.";
        path += sessionContext.getOnMobile() ? "mobile" : "desktop";
        path = sessionContext.getCustdir() + System.getProperty(path);
        sessionContext.setCssPath(path);

    }

}
