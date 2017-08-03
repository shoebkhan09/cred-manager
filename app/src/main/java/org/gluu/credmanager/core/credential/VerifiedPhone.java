package org.gluu.credmanager.core.credential;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gluu.credmanager.conf.CredentialType;

/**
 * Created by jgomer on 2017-07-25.
 */
public class VerifiedPhone extends RegisteredCredential {

    private String number;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private long addedOn;

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public VerifiedPhone(){
        setType(CredentialType.VERIFIED_PHONE);
    }

    public VerifiedPhone(String number){
        this.number=number;
        setType(CredentialType.VERIFIED_PHONE);
    }

    public long getAddedOn() {
        return addedOn;
    }

    public void setAddedOn(long addedOn) {
        this.addedOn = addedOn;
    }

    public String toString(){

        try {
            return new ObjectMapper().writeValueAsString(this);
        }
        catch (Exception e){
            return e.getMessage();
        }
    }
}