package org.gluu.credmngr;

import java.util.Date;

/**
 * Created by jgomer on 2017-06-23.
 */
public class Credential {

    public enum CredentialType{ SECURITY_KEY, VERIFIED_PHONE, SUPER_GLUU, OTP_DEVICE };

    private CredentialType type;
    private String identifier;
    private String nickname;
    private Date timeAdded;
    private Date lastUsed;

    public Credential(){

    }

    public Credential(CredentialType ct, String id, String nick, Date time, Date last){
        type=ct;
        identifier=id;
        nickname=nick;
        timeAdded=time;
        lastUsed=last;
    }

    public CredentialType getType() {
        return type;
    }

    public void setType(CredentialType type) {
        this.type = type;
    }

    public String getIdentifier() {
        return identifier;
    }

    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public Date getTimeAdded() {
        return timeAdded;
    }

    public void setTimeAdded(Date timeAdded) {
        this.timeAdded = timeAdded;
    }

    public Date getLastUsed() {
        return lastUsed;
    }

    public void setLastUsed(Date lastUsed) {
        this.lastUsed = lastUsed;
    }
}
