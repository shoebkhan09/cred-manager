/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.conf;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.misc.Utils;
import org.gluu.credmanager.services.ldap.pojo.CustomScript;
import org.xdi.model.SimpleCustomProperty;
import org.zkoss.util.resource.Labels;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by jgomer on 2017-07-22.
 * POJO storing values required for interacting with the Twilio service
 */
public class TwilioConfig {

    private static ObjectMapper mapper=new ObjectMapper();
    private static Logger logger = LogManager.getLogger(TwilioConfig.class);

    private String accountSID;
    private String authToken;
    private String fromNumber;

    public TwilioConfig(){ }

    public String getAuthToken() {
        return authToken;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public String getAccountSID() {
        return accountSID;
    }

    public void setAccountSID(String accountSID) {
        this.accountSID = accountSID;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

    public static TwilioConfig get(CustomScript smsScript) {

        Map<String, String> propsMap=Utils.getScriptProperties(smsScript.getProperties());

        TwilioConfig tc=new TwilioConfig();
        tc.setAccountSID(propsMap.get("twilio_sid"));
        tc.setAuthToken(propsMap.get("twilio_token"));
        tc.setFromNumber(propsMap.get("from_number"));

        List<String> values= Arrays.asList(tc.getAccountSID(), tc.getAuthToken(), tc.getFromNumber());
        if (values.stream().map(Utils::stringOptional).allMatch(Optional::isPresent)) {
            try {
                logger.info(Labels.getLabel("app.sms_settings"), mapper.writeValueAsString(tc));
            }
            catch (Exception e){
                logger.error(e.getMessage(), e);
            }
            return tc;
        }
        else
            return null;

    }

}