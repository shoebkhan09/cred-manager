/*
 * cred-manager is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
 *
 * Copyright (c) 2018, Gluu
 */
package org.gluu.credmanager.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents a registered credential of OTP type (verifiedMobile is not considered OTP device in this application)
 * @author jgomer
 */
public class OTPDevice extends RegisteredCredential implements Comparable<OTPDevice> {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private long addedOn;

    private int id;

    @JsonIgnore
    private String uid;

    public OTPDevice() {
    }

    public OTPDevice(String uid) {
        this.uid = uid;
        updateHash();
    }

    public OTPDevice(int id) {
        this.id = id;
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

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
        updateHash();
    }

    public void updateHash() {

        if (uid == null) {
            id = 0;
        } else {
            String str = uid.replaceFirst("hotp:", "").replaceFirst("totp:", "");
            int idx = str.indexOf(";");
            if (idx > 0) {
                str = str.substring(0, idx);
            }
            id = str.hashCode();
        }
    }

    public int compareTo(OTPDevice d2) {
        long date1 = getAddedOn();
        long date2 = d2.getAddedOn();
        return (date1 < date2) ? -1 : ((date1 > date2) ? 1 : 0);
    }

}
