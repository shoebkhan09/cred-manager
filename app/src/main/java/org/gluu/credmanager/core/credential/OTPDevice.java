package org.gluu.credmanager.core.credential;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.gluu.credmanager.conf.CredentialType;

/**
 * Created by jgomer on 2017-08-01.
 */
public class OTPDevice extends RegisteredCredential {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private long addedOn;

    private int id;

    public OTPDevice(){
        setType(CredentialType.OTP);
    }

    public OTPDevice(int id){
        this.id=id;
        setType(CredentialType.OTP);
    }

    public long getAddedOn() {
        return addedOn;
    }

    public void setAddedOn(long addedOn) {
        this.addedOn = addedOn;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

}
