/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2017, Gluu
 */
package org.gluu.credmanager.services;

import com.twilio.sdk.TwilioRestClient;
import com.twilio.sdk.resource.factory.MessageFactory;
import com.twilio.sdk.resource.instance.Message;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.gluu.credmanager.conf.AppConfiguration;
import org.gluu.credmanager.conf.TwilioConfig;
import org.gluu.credmanager.core.credential.VerifiedPhone;
import org.gluu.credmanager.services.ldap.LdapService;
import org.zkoss.util.resource.Labels;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by jgomer on 2017-08-18.
 * An app. scoped bean to serve the purpose of sending SMS using the Twilio service
 */
@ApplicationScoped
public class SmsService {

    @Inject
    AppConfiguration appConfig;

    @Inject
    LdapService ldapService;

    private Logger logger = LogManager.getLogger(getClass());

    private String fromNumber;
    private MessageFactory messageFactory;

    public SmsService(){ }

    public void sendSMS(String number, String body) throws Exception{

        List<NameValuePair> messageParams = new ArrayList<>();
        messageParams.add(new BasicNameValuePair("From", fromNumber));
        messageParams.add(new BasicNameValuePair("To", number));
        messageParams.add(new BasicNameValuePair("Body", body));
        Message message=messageFactory.create(messageParams);

        logger.info(Labels.getLabel("app.sms_sent_summary"), body, number, message.getStatus());

    }

    //Removes formating characters from LDAP-formatted phone numbers
    private static String rawNumber(String number){
        return number.replaceAll("[-\\+\\s]","");
    }

    public boolean isPhoneNumberUnique(VerifiedPhone phone) throws Exception{
        String rawPhone = rawNumber(phone.getNumber());
        return !ldapService.numberIsRegistered(phone.getNumber()) && !ldapService.numberIsRegistered(rawPhone);
    }

    @PostConstruct
    private void setup(){
        TwilioConfig twilioCfg=appConfig.getConfigSettings().getTwilioConfig();
        TwilioRestClient client =new TwilioRestClient(twilioCfg.getAccountSID(), twilioCfg.getAuthToken());

        fromNumber=twilioCfg.getFromNumber();
        messageFactory = client.getAccount().getMessageFactory();
    }

}
