package org.gluu.credmanager.conf.jsonized;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Created by jgomer on 2017-07-22.
 */
public class TwilioConfig {

    private String accountSID;
    private String authToken;
    private String fromNumber;

    public String getAuthToken() {
        return authToken;
    }

    public String getFromNumber() {
        return fromNumber;
    }

    public String getAccountSID() {
        return accountSID;
    }

    @JsonProperty("account_sid")
    public void setAccountSID(String accountSID) {
        this.accountSID = accountSID;
    }

    @JsonProperty("auth_token")
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    @JsonProperty("from_number")
    public void setFromNumber(String fromNumber) {
        this.fromNumber = fromNumber;
    }

}
