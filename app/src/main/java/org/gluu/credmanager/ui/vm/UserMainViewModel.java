package org.gluu.credmanager.ui.vm;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.core.WebUtils;
import org.gluu.credmanager.core.init.SessionListener;
import org.gluu.credmanager.services.ServiceMashup;
import org.zkoss.bind.annotation.Init;
import org.zkoss.zk.ui.Session;
import org.zkoss.zk.ui.select.annotation.WireVariable;

/**
 * Created by jgomer on 2017-07-08.
 */
//@VariableResolver(org.zkoss.zkplus.cdi.DelegatingVariableResolver.class)
public class UserMainViewModel {

    @WireVariable
    private Session session;

    private ServiceMashup services;

    private String message;

    public String getMessage() {
        //return message;
        /*AppConfiguration appcfg=services.getAppConfiguration();
        return "message" + (appcfg.isInOperableState());
        */
        return null;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private Logger logger = LogManager.getLogger(getClass());

    @Init
    public void init(){
        services= WebUtils.getServices(session);
    }

}