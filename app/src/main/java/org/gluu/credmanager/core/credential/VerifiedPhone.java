package org.gluu.credmanager.core.credential;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Created by jgomer on 2017-07-25.
 */
public class VerifiedPhone extends RegisteredCredential implements Comparable<VerifiedPhone> {

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
    }

    public VerifiedPhone(String number){
        this.number=number;
    }

    public long getAddedOn() {
        return addedOn;
    }

    public void setAddedOn(long addedOn) {
        this.addedOn = addedOn;
    }

    public int compareTo(VerifiedPhone ph){
        long date1=getAddedOn();
        long date2=ph.getAddedOn();
        return (date1 < date2) ? -1 : ((date1 > date2) ? 1 : 0);
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